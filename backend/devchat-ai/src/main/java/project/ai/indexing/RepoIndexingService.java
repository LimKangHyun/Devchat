package project.ai.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import project.ai.client.internal.InternalAuthClient;
import project.ai.indexing.chunking.AstChunkExtractor;
import project.ai.stream.index.RepoIndexResultProducer;
import project.ai.indexing.chunking.ChunkMeta;
import project.ai.indexing.chunking.ChunkEmbeddingText;
import project.ai.client.QdrantClient;
import project.common.exception.errorcode.IndexingErrorCode;
import project.common.exception.ex.IndexingException;
import project.common.message.index.FileReindexMessage;
import project.common.message.index.IndexingKeys;
import project.common.message.index.IndexingProgressMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepoIndexingService {

    /**
     * 인덱싱 락 TTL. 짧게 잡고 진행하는 동안 하트비트로 연장한다.
     * 서버가 죽으면 연장이 멈춰 2분 안에 락이 사라지고, API 서버가 이를 보고 남은 RUNNING을 정리한다.
     * 예전에는 30분 고정이라, 죽어도 30분간 락이 남고 30분 넘는 인덱싱은 락이 먼저 풀렸다.
     */
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);
    private static final long LOCK_HEARTBEAT_SECONDS = 30;
    /** 취소 플래그는 배치 하나가 오래 걸려도(호출 한도 대기 등) 다음 확인 때까지 남아 있어야 한다. */
    private static final Duration CANCEL_TTL = Duration.ofMinutes(30);

    /** 인덱싱 락 연장 전용 스레드. 인덱싱 스레드가 오래 막혀 있어도 연장은 따로 돈다. */
    private final ScheduledExecutorService lockHeartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "indexing-lock-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private static final int MAX_WAIT_SECONDS = 60;
    private static final long POLL_INTERVAL_MS = 500;
    private static final int BATCH_SIZE = 100;
    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024;

    /**
     * 배치 간 병렬 처리 동시성 제한.
     * Gemini TPM/RPM을 고려해 4로 시작 → 실측 후 8/16으로 조정 예정.
     */
    private static final int BATCH_CONCURRENCY = 2;

    private static final Set<String> EXCLUDED_DIRS = Set.of(
        "node_modules", ".git", "build", "out", "target", ".gradle", "test", "dto"
    );

    private static final Set<String> EXCLUDED_KEYWORDS = Set.of(
        "exception", "mapper"
    );

    @Qualifier("batchIndexingExecutor")
    private final ExecutorService batchIndexingExecutor;
    private final EmbeddingService embeddingService;
    private final SparseEncoder sparseEncoder;
    private final QdrantClient qdrantClient;

    private final StringRedisTemplate redisTemplate;

    private final InternalAuthClient internalAuthClient;
    private final RepoIndexResultProducer repoIndexResultProducer;
    private final AstChunkExtractor astChunkExtractor;
    private final IndexingProgressReporter progressReporter;

    /** 임베딩할 때 청크 앞에 맥락 머리말을 붙일지. 설명은 embeddingTextOf() 참고. 바꾸면 재인덱싱해야 한다. */
    @org.springframework.beans.factory.annotation.Value("${rag.indexing.contextual-header:true}")
    private boolean contextualHeaderEnabled;

    /** 레포 간 동시 인덱싱 제한 (inter-request) */
    private final Semaphore semaphore = new Semaphore(4);

    /** 배치 간 동시 처리 제한 (intra-request, Gemini Rate Limit 대응) */
    private final Semaphore batchSemaphore = new Semaphore(BATCH_CONCURRENCY);

    @Async("repoIndexingExecutor")
    public void indexRepository(Long repoId, String repoUrl, Long memberId) {
        String lockKey = IndexingKeys.lockKey(repoId);

        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("이미 인덱싱 중인 레포. repoId={}", repoId);
            return;
        }

        // 이전 실행에서 남은 취소 플래그가 이번 인덱싱을 바로 멈추지 않게 지운다.
        // 이 시점 이후에 들어온 취소만 이번 실행에 적용된다.
        clearCancelFlag(repoId);

        // 락 하트비트: 진행하는 동안 락을 계속 연장한다. 서버가 죽으면 연장이 멈춰 락이 곧 사라진다.
        ScheduledFuture<?> heartbeat = lockHeartbeat.scheduleAtFixedRate(() -> {
            try {
                redisTemplate.expire(lockKey, LOCK_TTL);
            } catch (Exception e) {
                log.warn("인덱싱 락 연장 실패. repoId={}", repoId, e);
            }
        }, LOCK_HEARTBEAT_SECONDS, LOCK_HEARTBEAT_SECONDS, TimeUnit.SECONDS);

        // 운영 체제의 임시 폴더를 쓴다. 예전에는 "C:\dc"로 고정돼, Linux에서는 작업 폴더 안에 그 이름의 폴더가 생겼다.
        Path repoPath = null;
        boolean permitAcquired = false;

        try {
            semaphore.acquire();
            permitAcquired = true;
            repoPath = Files.createTempDirectory("devchat-index-" + repoId + "-");
            // 다른 저장소 인덱싱이 끝나기를 기다리는 동안 취소됐을 수 있다.
            throwIfCancelled(repoId);

            long startTime = System.currentTimeMillis();
            log.info("레포 인덱싱 시작. repoId={}", repoId);
            progressReporter.report(repoId, IndexingProgressMessage.CLONING, 0, 0);

            String token = internalAuthClient.getGithubToken(memberId);

            long cloneStart = System.currentTimeMillis();
            log.info("[{}] git clone 시작. repoId={}", Thread.currentThread().getName(), repoId);
            cloneRepo(repoUrl, token, repoPath);
            log.info("[{}] git clone 완료. repoId={}, 소요={}ms",
                Thread.currentThread().getName(), repoId, System.currentTimeMillis() - cloneStart);
            throwIfCancelled(repoId);

            processAndIndex(repoId, repoPath);

            log.info("레포 인덱싱 완료. repoId={}, 소요시간={}ms", repoId, System.currentTimeMillis() - startTime);
            repoIndexResultProducer.publishSuccess(repoId);

        } catch (IndexingCancelledException e) {
            cleanUpCancelled(repoId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("레포 인덱싱 인터럽트. repoId={}", repoId, e);
            cleanUpFailed(repoId, "인덱싱 인터럽트");
        } catch (Exception e) {
            log.error("레포 인덱싱 실패. repoId={}", repoId, e);
            cleanUpFailed(repoId, e.getMessage());
        } finally {
            if (permitAcquired) semaphore.release();
            if (repoPath != null) deleteDirectory(repoPath);
            progressReporter.clear(repoId);
            clearCancelFlag(repoId);
            heartbeat.cancel(false);
            redisTemplate.delete(IndexingKeys.requestedKey(repoId));
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 취소된 인덱싱의 뒷정리. 이 저장소의 청크를 전부 지운 뒤에 "취소됨"을 알린다.
     * indexFiles가 진행 중이던 배치가 모두 끝난 뒤에 예외를 던지므로, 지운 뒤에 다시 저장되는 청크는 없다.
     */
    private void cleanUpCancelled(Long repoId) {
        log.info("인덱싱 취소, 저장한 청크 삭제. repoId={}", repoId);
        deleteSavedChunks(repoId);
        repoIndexResultProducer.publishCancelled(repoId);
    }

    /**
     * 실패한 인덱싱의 뒷정리. 일부만 저장된 인덱스는 검색 품질을 들쭉날쭉하게 만들므로 지우고 "실패"를 알린다.
     * 방장이 다시 시작하면 처음부터 저장한다. 취소와 마찬가지로 진행 중이던 배치가 모두 끝난 뒤에 지운다.
     */
    private void cleanUpFailed(Long repoId, String reason) {
        log.info("인덱싱 실패, 저장한 청크 삭제. repoId={}", repoId);
        deleteSavedChunks(repoId);
        repoIndexResultProducer.publishFail(repoId, reason);
    }

    private void deleteSavedChunks(Long repoId) {
        try {
            qdrantClient.deleteRepo(String.valueOf(repoId));
        } catch (Exception e) {
            // 지우지 못하면 찌꺼기가 남으므로 반드시 드러나야 한다. 채팅방 삭제 시 삭제 요청으로 한 번 더 지운다.
            log.error("인덱싱 청크 삭제 실패. repoId={}", repoId, e);
        }
    }

    /** 인덱싱과 같은 규칙으로 청킹한 결과. 청크 순서가 곧 ChunkId의 chunkIndex다. */
    public List<ChunkMeta> chunkForIndexing(String content, String relativePath) {
        return chunk(content, relativePath);
    }

    private List<ChunkMeta> chunk(String content, String relativePath) {
        List<ChunkMeta> chunks = astChunkExtractor.extract(content, relativePath); // relativePath 파라미터 제거 후
        if (!chunks.isEmpty()) return chunks;
        return slidingWindow(content, 100, 10).stream()
            .map(ChunkMeta::fallback)
            .toList();
    }

    public void reindexFile(FileReindexMessage message) {
        Long roomId = message.roomId();
        String namespace = String.valueOf(roomId);
        String filePath = message.filePath();

        try {
            if ("removed".equals(message.status())) {
                qdrantClient.deleteFileChunks(namespace, filePath);
                log.info("파일 삭제 반영 완료. roomId={}, filePath={}", roomId, filePath);
                return;
            }

            // 기존 청크를 먼저 지우면, 임베딩이 실패했을 때(키 소진 등) 이 파일의 청크가
            // 통째로 사라진 채 남는다. 그래서 새 청크를 먼저 저장(같은 ID는 덮어씀)하고,
            // 저장이 끝난 뒤에 이번에 덮어쓰지 않은 옛 청크만 지운다.
            // flushBatch는 임베딩이 끝나야 upsert하므로, 임베딩이 실패하면 옛 청크가 그대로 남는다.
            List<String> existingIds = qdrantClient.listFileChunkIds(namespace, filePath);

            String content = message.fileContent();
            List<ChunkMeta> metas = new ArrayList<>();
            if (content != null && !content.isBlank()) {
                List<ChunkMeta> chunks = chunk(content, filePath);
                for (int i = 0; i < chunks.size(); i++) {
                    String id = ChunkId.of(roomId, filePath, i);
                    metas.add(chunks.get(i).withIndexingInfo(id, filePath, i));
                }
            }

            if (!metas.isEmpty()) {
                flushBatch(roomId, metas);
            }

            Set<String> newIds = new HashSet<>();
            metas.forEach(m -> newIds.add(m.id()));
            List<String> staleIds = existingIds.stream()
                .filter(id -> !newIds.contains(id))
                .toList();
            qdrantClient.deleteByIds(staleIds);

            // 이름이 바뀐 파일은 옛 경로의 청크가 남아 있으므로, 새 경로 저장이 끝난 뒤 지운다.
            if (message.previousFilePath() != null) {
                qdrantClient.deleteFileChunks(namespace, message.previousFilePath());
            }

            log.info("파일 재인덱싱 완료. roomId={}, filePath={}, previousFilePath={}, chunkCount={}, 삭제된 옛 청크={}",
                roomId, filePath, message.previousFilePath(), metas.size(), staleIds.size());
        } catch (IndexingException e) {
            if (e.getErrorCode() == IndexingErrorCode.EMBEDDING_EXHAUSTED) {
                log.error("임베딩 키 소진 - 파일 재인덱싱 중단. roomId={}, filePath={}", roomId, filePath);
            } else {
                log.error("파일 재인덱싱 실패. roomId={}, filePath={}", roomId, filePath, e);
            }
        } catch (Exception e) {
            log.error("파일 재인덱싱 실패. roomId={}, filePath={}", roomId, filePath, e);
        }
    }

    /** 진행 중인 인덱싱을 멈추게 한다. 진행 중이 아니면 다음 실행 시작 때 지워진다. */
    public void cancelIndexing(Long repoId) {
        redisTemplate.opsForValue().set(IndexingKeys.cancelKey(repoId), "1", CANCEL_TTL);
        log.info("인덱싱 취소 플래그 설정. repoId={}", repoId);
    }

    private boolean isCancelled(Long repoId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(IndexingKeys.cancelKey(repoId)));
        } catch (Exception e) {
            log.warn("Redis 장애로 cancel 체크 실패. repoId={}", repoId, e);
            return false;
        }
    }

    private void throwIfCancelled(Long repoId) {
        if (isCancelled(repoId)) throw new IndexingCancelledException(repoId);
    }

    private void clearCancelFlag(Long repoId) {
        try {
            redisTemplate.delete(IndexingKeys.cancelKey(repoId));
        } catch (Exception e) {
            log.warn("취소 플래그 삭제 실패. repoId={}", repoId, e);
        }
    }

    private void cloneRepo(String repoUrl, String token, Path targetPath) throws IOException, InterruptedException {
        String authenticatedUrl = repoUrl.replace("https://", "https://oauth2:" + token + "@");
        Files.createDirectories(targetPath);

        String gitPath = System.getProperty("os.name").toLowerCase().contains("win")
            ? "C:\\Program Files\\Git\\bin\\git.exe"
            : "git";

        ProcessBuilder pb = new ProcessBuilder(
            gitPath, "clone", "--depth", "1", authenticatedUrl, targetPath.toString()
        );
        pb.environment().remove("GIT_ASKPASS");
        pb.redirectErrorStream(true);

        Process process = pb.start();

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            // 토큰이 URL에 포함돼 있으니 로그에 그대로 찍히지 않게 마스킹
            String safeOutput = output.replace(token, "****");
            throw new IOException("git clone 실패. exitCode=" + exitCode + ", output=" + safeOutput);
        }
    }

    /** 로컬 디렉터리에서 인덱싱 대상 파일 목록. 운영 인덱싱과 같은 제외 규칙을 쓴다. */
    public List<Path> collectIndexableFiles(Path repoPath) throws IOException {
        return collectFiles(repoPath);
    }

    /**
     * 이미 내려받은 로컬 디렉터리의 지정 파일들만 인덱싱한다. clone·락·결과 이벤트 없이 청킹과 저장만 한다.
     * 검색 품질 평가에서 고정된 커밋 스냅샷을 조금씩 나눠 인덱싱하려고 쓴다(임베딩 호출 한도 대응).
     */
    public void indexLocalFiles(Long repoId, Path repoPath, List<Path> files) {
        indexFiles(repoId, repoPath, files, false);
    }

    private void processAndIndex(Long repoId, Path repoPath) throws IOException {
        List<Path> javaFiles = collectFiles(repoPath);
        log.info("인덱싱 대상 파일 수: {}. repoId={}", javaFiles.size(), repoId);
        indexFiles(repoId, repoPath, javaFiles, true);
    }

    private List<Path> collectFiles(Path repoPath) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(repoPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (EXCLUDED_DIRS.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String filePath = file.toString().toLowerCase();
                if (!filePath.endsWith(".java")) return FileVisitResult.CONTINUE;
                if (attrs.size() > MAX_FILE_SIZE_BYTES) return FileVisitResult.CONTINUE;
                if (EXCLUDED_KEYWORDS.stream().anyMatch(filePath::contains)) return FileVisitResult.CONTINUE;
                javaFiles.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return javaFiles;
    }

    /**
     * 파일 목록을 청킹 → 배치 수집 → 배치 병렬 처리.
     * BATCH_CONCURRENCY 개의 배치를 동시에 처리하며 Gemini Rate Limit을 준수한다.
     *
     * 취소되면 IndexingCancelledException을 던진다. 저장 단계에서는 진행 중이던 배치가 모두 끝난 뒤에
     * 던지므로, 호출한 쪽이 청크를 지운 뒤에 늦게 저장되는 배치가 없다.
     *
     * @param reportProgress 화면에 진행률을 알릴지. 평가용 로컬 인덱싱은 끈다.
     */
    private void indexFiles(Long repoId, Path repoPath, List<Path> javaFiles, boolean reportProgress) {
        long chunkStartTotal = System.currentTimeMillis();

        // 1. 청킹 → 전체 배치 목록 수집
        List<List<ChunkMeta>> batches = new ArrayList<>();
        List<ChunkMeta> buffer = new ArrayList<>();
        int processedFiles = 0;

        for (Path file : javaFiles) {
            if (isCancelled(repoId)) {
                log.info("인덱싱 취소 감지 (청킹 단계). repoId={}", repoId);
                throw new IndexingCancelledException(repoId);
            }
            if (reportProgress) {
                progressReporter.report(repoId, IndexingProgressMessage.CHUNKING, processedFiles, javaFiles.size());
            }
            processedFiles++;
            try {
                String content = Files.readString(file);
                if (content.isBlank()) continue;

                String relativePath = repoPath.relativize(file).toString().replace('\\', '/');
                List<ChunkMeta> chunks = chunk(content, relativePath);

                for (int i = 0; i < chunks.size(); i++) {
                    String id = ChunkId.of(repoId, relativePath, i);
                    buffer.add(chunks.get(i).withIndexingInfo(id, relativePath, i));
                }

                while (buffer.size() >= BATCH_SIZE) {
                    batches.add(new ArrayList<>(buffer.subList(0, BATCH_SIZE)));
                    buffer = new ArrayList<>(buffer.subList(BATCH_SIZE, buffer.size()));
                }
            } catch (Exception e) {
                log.warn("파일 청킹 실패, 스킵. file={}", file, e);
            }
        }
        if (!buffer.isEmpty()) batches.add(buffer);

        long chunkMs = System.currentTimeMillis() - chunkStartTotal;
        log.info("배치 수집 완료. repoId={}, 총 배치수={}", repoId, batches.size());

        long embedStartTotal = System.currentTimeMillis();
        int totalChunks = batches.stream().mapToInt(List::size).sum();
        java.util.concurrent.atomic.AtomicInteger savedChunks = new java.util.concurrent.atomic.AtomicInteger();
        if (reportProgress) {
            progressReporter.report(repoId, IndexingProgressMessage.EMBEDDING, 0, totalChunks);
        }

        // 2. 배치 병렬 처리
        // 배치는 한꺼번에 큐에 들어가므로, 넣는 시점이 아니라 각 배치가 실제로 저장하기 직전에 취소를 확인한다.
        List<Future<Void>> futures = new ArrayList<>();
        for (List<ChunkMeta> batch : batches) {
            futures.add(batchIndexingExecutor.submit(() -> {
                batchSemaphore.acquire();
                try {
                    if (isCancelled(repoId)) return null;
                    flushBatch(repoId, batch);
                    int saved = savedChunks.addAndGet(batch.size());
                    if (reportProgress) {
                        progressReporter.report(repoId, IndexingProgressMessage.EMBEDDING, saved, totalChunks);
                    }
                } finally {
                    batchSemaphore.release();
                }
                return null;
            }));
        }

        // 실패한 배치가 있어도 나머지 배치가 모두 끝날 때까지 기다린다.
        // 먼저 빠져나오면 그 뒤에 저장되는 배치가 생겨, 취소 후 청크를 지워도 찌꺼기가 남을 수 있다.
        Throwable firstFailure = null;
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                if (firstFailure == null) firstFailure = e.getCause();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("배치 병렬 처리 인터럽트. repoId=" + repoId, e);
            }
        }

        // 모든 배치가 끝난(또는 건너뛴) 뒤에 판단해야, 이후 청크를 지울 때 늦게 저장되는 배치가 없다.
        if (isCancelled(repoId)) {
            log.info("인덱싱 취소 감지 (저장 단계). repoId={}, 저장된 청크={}/{}", repoId, savedChunks.get(), totalChunks);
            throw new IndexingCancelledException(repoId);
        }
        if (firstFailure != null) {
            if (firstFailure instanceof IndexingException ie) {
                if (ie.getErrorCode() == IndexingErrorCode.EMBEDDING_EXHAUSTED) {
                    log.error("임베딩 키 소진 - 인덱싱 중단. repoId={}", repoId);
                }
                throw ie;
            }
            throw new RuntimeException("배치 병렬 처리 실패. repoId=" + repoId, firstFailure);
        }

        long embedMs = System.currentTimeMillis() - embedStartTotal;

        log.info("[측정] repoId={} 동시성={} 파일={} 배치={} 청킹={}ms 임베딩+업서트={}ms 총={}ms API호출={}",
            repoId, BATCH_CONCURRENCY, javaFiles.size(), batches.size(),
            chunkMs, embedMs, chunkMs + embedMs, embeddingService.getAndResetCount());
    }

    /**
     * 청크 하나의 payload. 임베딩 없이 메타데이터만 다시 계산할 때(추출 규칙 변경 후 평가 등)도
     * 인덱싱과 똑같은 형식이 나오도록 한곳에서 만든다. meta는 withIndexingInfo가 적용된 상태여야 한다.
     */
    public Map<String, Object> payloadOf(Long repoId, ChunkMeta meta) {
        String code = meta.chunk().length() > 2500 ? meta.chunk().substring(0, 2500) : meta.chunk();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("repoId", String.valueOf(repoId));
        metadata.put("filePath", meta.relativePath());
        metadata.put("chunkIndex", String.valueOf(meta.chunkIndex()));
        metadata.put("code", code);
        metadata.put("language", "java");
        metadata.put("className", meta.className() != null ? meta.className() : "");
        metadata.put("methodName", meta.methodName() != null ? meta.methodName() : "");
        metadata.put("methodSignature", meta.methodSignature() != null ? meta.methodSignature() : "");
        metadata.put("packageName", meta.packageName() != null ? meta.packageName() : "");
        metadata.put("superClassName", meta.superClassName() != null ? meta.superClassName() : "");
        metadata.put("interfaceNames", meta.interfaceNames() != null ? meta.interfaceNames() : List.of());
        metadata.put("calledMethodNames", meta.calledMethodNames() != null ? meta.calledMethodNames() : List.of());
        metadata.put("calledMethodQualified", meta.calledMethodQualified() != null ? meta.calledMethodQualified() : List.of());
        metadata.put("referencedTypeNames", meta.referencedTypeNames() != null ? meta.referencedTypeNames() : List.of());
        metadata.put("annotations", meta.annotations() != null ? meta.annotations() : List.of());
        metadata.put("calledMethodRefs", toCalledMethodRefPayload(meta));
        return metadata;
    }

    /** 임베딩·sparse 인코딩에 쓸 텍스트. 맥락 머리말을 켜면 ChunkEmbeddingText 형식, 끄면 코드만. 저장하는 code는 원문 그대로다. */
    public String embeddingTextOf(ChunkMeta meta) {
        return contextualHeaderEnabled ? ChunkEmbeddingText.withHeader(meta) : meta.chunk();
    }

    private void flushBatch(Long repoId, List<ChunkMeta> batch) {
        List<String> texts = batch.stream().map(this::embeddingTextOf).toList();

        long embedStart = System.currentTimeMillis();
        log.info("[{}] 임베딩 호출 시작. repoId={}, chunkCount={}",
            Thread.currentThread().getName(), repoId, batch.size());
        List<float[]> vectors = embeddingService.embedDocuments(texts);
        log.info("[{}] 임베딩 호출 완료. repoId={}, 소요={}ms",
            Thread.currentThread().getName(), repoId, System.currentTimeMillis() - embedStart);

        long upsertStart = System.currentTimeMillis();
        List<QdrantClient.Point> points = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            ChunkMeta meta = batch.get(i);
            // 키워드 검색용 sparse는 잘리지 않은 청크 전문으로 만든다(payload의 code는 2500자에서 잘림).
            points.add(new QdrantClient.Point(meta.id(), vectors.get(i),
                sparseEncoder.encodeDocument(texts.get(i)), payloadOf(repoId, meta)));
        }

        qdrantClient.upsert(points);
        log.info("[{}] 벡터 upsert 완료. repoId={}, 청크수={}, 소요={}ms",
            Thread.currentThread().getName(), repoId, batch.size(), System.currentTimeMillis() - upsertStart);
    }

    /**
     * 이 청크(메서드)가 하는 호출들을 객체 배열로 저장한다. nested 필터로
     * "같은 호출 안에서 메서드 이름 = X 이고 대상 클래스 = Y" 같은 조건을 걸기 위해서다.
     *
     * calledMethodQualified는 대상 클래스가 확정된 호출만 담지만, 여기에는 이름 관례로 추정한
     * 호출(resolved=false)도 담는다. 대상 클래스를 모르는 호출(체이닝 등)은 targetClassHint를 비운다.
     * fromMethodName은 이 청크의 메서드 자신이라 검색에 쓸 일이 없어 저장하지 않는다.
     */
    private List<Map<String, Object>> toCalledMethodRefPayload(ChunkMeta meta) {
        if (meta.calledMethodRefs() == null) return List.of();
        return meta.calledMethodRefs().stream()
            .map(ref -> {
                Map<String, Object> obj = new HashMap<>();
                obj.put("methodName", ref.methodName());
                if (ref.targetClassHint() != null) obj.put("targetClassHint", ref.targetClassHint());
                obj.put("resolved", ref.resolved());
                return obj;
            })
            .toList();
    }

    private List<String> slidingWindow(String content, int windowSize, int overlap) {
        String[] lines = content.split("\n");
        List<String> chunks = new ArrayList<>();

        if (lines.length < 10) return chunks;

        int step = windowSize - overlap;
        for (int i = 0; i < lines.length; i += step) {
            int end = Math.min(i + windowSize, lines.length);
            chunks.add(String.join("\n", Arrays.copyOfRange(lines, i, end)));
            if (end == lines.length) break;
        }
        return chunks;
    }

    private void deleteDirectory(Path path) {
        try {
            if (!Files.exists(path)) return;
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    file.toFile().setWritable(true);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("/tmp 디렉토리 삭제 실패. path={}", path, e);
        }
    }

    private boolean isIndexing(Long repoId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(IndexingKeys.lockKey(repoId)));
    }

    public void waitUntilIndexingDone(Long repoId) {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(MAX_WAIT_SECONDS);

        while (isIndexing(repoId)) {
            if (System.currentTimeMillis() > deadline) {
                log.warn("인덱싱 완료 대기 타임아웃. repoId={}", repoId);
                return;
            }
            sleep();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(RepoIndexingService.POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<String> chunkOnlyForMeasurement(String repoUrl, Long memberId) throws IOException, InterruptedException {
        Path repoPath = Files.createTempDirectory("devchat-measure-");
        try {
            String token = internalAuthClient.getGithubToken(memberId);
            cloneRepo(repoUrl, token, repoPath);

            List<Path> files = collectFiles(repoPath);
            log.info("[측정] 청킹 대상 파일 수: {}", files.size());

            List<String> allChunks = new ArrayList<>();
            for (Path file : files) {
                String content = Files.readString(file);
                if (content.isBlank()) continue;
                String relativePath = repoPath.relativize(file).toString().replace('\\', '/');
                allChunks.addAll(chunk(content, relativePath).stream().map(ChunkMeta::chunk).toList());
            }

            log.info("[측정] 총 청크 수: {}", allChunks.size());
            return allChunks;
        } finally {
            deleteDirectory(repoPath);
        }
    }

    public List<String> chunkOnlyForMeasurement(String repoUrl) throws IOException, InterruptedException {
        Path repoPath = Paths.get(
            System.getProperty("java.io.tmpdir"), "devchat-measure", UUID.randomUUID().toString()
        );
        try {
            cloneRepoPublicOnly(repoUrl, repoPath);

            List<Path> files = collectFiles(repoPath);
            log.info("[측정] 청킹 대상 파일 수: {}", files.size());

            List<String> allChunks = new ArrayList<>();
            for (Path file : files) {
                String content = Files.readString(file);
                if (content.isBlank()) continue;
                String relativePath = repoPath.relativize(file).toString().replace('\\', '/');
                allChunks.addAll(chunk(content, relativePath).stream().map(ChunkMeta::chunk).toList());
            }

            log.info("[측정] 총 청크 수: {}", allChunks.size());
            return allChunks;
        } finally {
            deleteDirectory(repoPath);
        }
    }

    private void cloneRepoPublicOnly(String repoUrl, Path targetPath) throws IOException, InterruptedException {
        Files.createDirectories(targetPath);

        String gitPath = System.getProperty("os.name").toLowerCase().contains("win")
            ? "C:\\Program Files\\Git\\bin\\git.exe"
            : "git";

        ProcessBuilder pb = new ProcessBuilder(
            gitPath, "clone", "--depth", "1", repoUrl, targetPath.toString()
        );
        pb.environment().remove("GIT_ASKPASS");
        pb.redirectErrorStream(true);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("git clone 실패. exitCode=" + exitCode);
        }
    }

    public List<String> chunkOnlyForMeasurementSlidingOnly(String repoUrl) throws IOException, InterruptedException {
        Path repoPath = Paths.get(
            System.getProperty("java.io.tmpdir"), "devchat-measure", UUID.randomUUID().toString()
        );
        try {
            cloneRepoPublicOnly(repoUrl, repoPath);

            List<Path> files = collectFiles(repoPath);
            log.info("[측정-슬라이딩] 청킹 대상 파일 수: {}", files.size());

            List<String> allChunks = new ArrayList<>();
            for (Path file : files) {
                String content = Files.readString(file);
                if (content.isBlank()) continue;
                allChunks.addAll(slidingWindow(content, 100, 10)); // AST 안 거치고 무조건 슬라이딩 윈도우
            }

            log.info("[측정-슬라이딩] 총 청크 수: {}", allChunks.size());
            return allChunks;
        } finally {
            deleteDirectory(repoPath);
        }
    }
}