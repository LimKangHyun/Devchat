package project.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import project.ai.client.PineconeClient;
import project.ai.internal.InternalAuthClient;
import project.ai.service.chunker.AstChunkExtractor;
import project.ai.stream.index.RepoIndexResultProducer;
import project.ai.service.chunker.ChunkMeta;
import project.common.exception.errorcode.IndexingErrorCode;
import project.common.exception.ex.IndexingException;
import project.common.message.index.FileReindexMessage;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepoIndexingService {

    private static final String LOCK_PREFIX = "repo:indexing:";
    private static final String CANCEL_PREFIX = "repo:indexing:cancel:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int MAX_WAIT_SECONDS = 60;
    private static final long POLL_INTERVAL_MS = 500;
    private static final int BATCH_SIZE = 100;
    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024;

    /**
     * 배치 간 병렬 처리 동시성 제한.
     * Gemini TPM/RPM을 고려해 4로 시작 → 실측 후 8/16으로 조정 예정.
     */
    private static final int BATCH_CONCURRENCY = 1;

    private static final Set<String> EXCLUDED_DIRS = Set.of(
        "node_modules", ".git", "build", "out", "target", ".gradle", "test", "dto"
    );

    private static final Set<String> EXCLUDED_KEYWORDS = Set.of(
        "dto", "config", "exception", "mapper"
    );

    @Qualifier("batchIndexingExecutor")
    private final ExecutorService batchIndexingExecutor;
    private final EmbeddingService embeddingService;
    private final PineconeClient pineconeClient;

    @Qualifier("streamRedisTemplate")
    private final RedisTemplate<String, String> redisTemplate;

    private final InternalAuthClient internalAuthClient;
    private final RepoIndexResultProducer repoIndexResultProducer;
    private final AstChunkExtractor astChunkExtractor;

    /** 레포 간 동시 인덱싱 제한 (inter-request) */
    private final Semaphore semaphore = new Semaphore(4);

    /** 배치 간 동시 처리 제한 (intra-request, Gemini Rate Limit 대응) */
    private final Semaphore batchSemaphore = new Semaphore(BATCH_CONCURRENCY);

    @Async("repoIndexingExecutor")
    public void indexRepository(Long repoId, String repoUrl, Long memberId) {
        String lockKey = LOCK_PREFIX + repoId;

        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("이미 인덱싱 중인 레포. repoId={}", repoId);
            return;
        }

        Path repoPath = Paths.get(
            System.getProperty("java.io.tmpdir"), "devchat", UUID.randomUUID().toString()
        );

        try {
            semaphore.acquire();
            long startTime = System.currentTimeMillis();
            log.info("레포 인덱싱 시작. repoId={}", repoId);

            String token = internalAuthClient.getGithubToken(memberId);

            long cloneStart = System.currentTimeMillis();
            log.info("[{}] git clone 시작. repoId={}", Thread.currentThread().getName(), repoId);
            cloneRepo(repoUrl, token, repoPath);
            log.info("[{}] git clone 완료. repoId={}, 소요={}ms",
                Thread.currentThread().getName(), repoId, System.currentTimeMillis() - cloneStart);

            processAndIndex(repoId, repoPath);

            log.info("레포 인덱싱 완료. repoId={}, 소요시간={}ms", repoId, System.currentTimeMillis() - startTime);
            repoIndexResultProducer.publishSuccess(repoId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("레포 인덱싱 인터럽트. repoId={}", repoId, e);
            repoIndexResultProducer.publishFail(repoId, "인덱싱 인터럽트");
        } catch (Exception e) {
            log.error("레포 인덱싱 실패. repoId={}", repoId, e);
            repoIndexResultProducer.publishFail(repoId, e.getMessage());
        } finally {
            semaphore.release();
            deleteDirectory(repoPath);
            redisTemplate.delete(lockKey);
        }
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
            pineconeClient.deleteFileChunks(namespace, filePath, namespace);

            if ("removed".equals(message.status())) {
                log.info("파일 삭제 반영 완료. roomId={}, filePath={}", roomId, filePath);
                return;
            }

            String content = message.fileContent();
            if (content == null || content.isBlank()) {
                log.info("파일 내용 없음, 재인덱싱 스킵. roomId={}, filePath={}", roomId, filePath);
                return;
            }

            List<ChunkMeta> chunks = chunk(content, filePath);
            List<ChunkMeta> metas = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String id = roomId + "-" + filePath.replace("/", "_") + "-" + i;
                metas.add(chunks.get(i).withIndexingInfo(id, filePath, i));
            }

            if (!metas.isEmpty()) {
                flushBatch(roomId, metas);
            }

            log.info("파일 재인덱싱 완료. roomId={}, filePath={}, chunkCount={}", roomId, filePath, metas.size());
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

    public void cancelIndexing(Long repoId) {
        redisTemplate.opsForValue().set(CANCEL_PREFIX + repoId, "1", LOCK_TTL);
        log.info("인덱싱 취소 플래그 설정. repoId={}", repoId);
    }

    private boolean isCancelled(Long repoId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(CANCEL_PREFIX + repoId));
        } catch (Exception e) {
            log.warn("Redis 장애로 cancel 체크 실패. repoId={}", repoId, e);
            return false;
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
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("git clone 실패. exitCode=" + exitCode);
        }
    }

    private void processAndIndex(Long repoId, Path repoPath) throws IOException {
        List<Path> javaFiles = collectFiles(repoPath);
        log.info("인덱싱 대상 파일 수: {}. repoId={}", javaFiles.size(), repoId);
        indexFiles(repoId, repoPath, javaFiles);
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
     */
    private void indexFiles(Long repoId, Path repoPath, List<Path> javaFiles) {
        // 1. 청킹 → 전체 배치 목록 수집
        List<List<ChunkMeta>> batches = new ArrayList<>();
        List<ChunkMeta> buffer = new ArrayList<>();

        for (Path file : javaFiles) {
            if (isCancelled(repoId)) {
                log.info("인덱싱 취소 감지 (청킹 단계). repoId={}", repoId);
                return;
            }
            try {
                String content = Files.readString(file);
                if (content.isBlank()) continue;

                String relativePath = repoPath.relativize(file).toString();
                long chunkStart = System.currentTimeMillis();
                List<ChunkMeta> chunks = chunk(content, relativePath);
                log.info("[{}] 청킹 완료. file={}, chunkCount={}, 소요={}ms",
                    Thread.currentThread().getName(), relativePath, chunks.size(),
                    System.currentTimeMillis() - chunkStart);

                for (int i = 0; i < chunks.size(); i++) {
                    String id = repoId + "-" + relativePath.replace("/", "_") + "-" + i;
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

        log.info("배치 수집 완료. repoId={}, 총 배치수={}", repoId, batches.size());

        // 2. 배치 병렬 처리
        // 2. 배치 병렬 처리
        List<Future<Void>> futures = new ArrayList<>();
        for (List<ChunkMeta> batch : batches) {
            if (isCancelled(repoId)) {
                log.info("인덱싱 취소 감지 (배치 처리 단계). repoId={}", repoId);
                break;
            }
            futures.add(batchIndexingExecutor.submit(() -> {
                batchSemaphore.acquire();
                try {
                    flushBatch(repoId, batch);
                } finally {
                    batchSemaphore.release();
                }
                return null;
            }));
        }

        try {
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IndexingException ie) {
                if (ie.getErrorCode() == IndexingErrorCode.EMBEDDING_EXHAUSTED) {
                    log.error("임베딩 키 소진 - 인덱싱 중단. repoId={}", repoId);
                }
                throw ie;
            }
            throw new RuntimeException("배치 병렬 처리 실패. repoId=" + repoId, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("배치 병렬 처리 인터럽트. repoId=" + repoId, e);
        }

        log.info("인덱싱 완료. repoId={}, 총 API 호출 횟수={}", repoId, embeddingService.getAndResetCount());
    }

    private void flushBatch(Long repoId, List<ChunkMeta> batch) {
        List<String> texts = batch.stream().map(ChunkMeta::chunk).toList();

        long embedStart = System.currentTimeMillis();
        log.info("[{}] 임베딩 호출 시작. repoId={}, chunkCount={}",
            Thread.currentThread().getName(), repoId, batch.size());
        List<float[]> vectors = embeddingService.embedBatch(texts);
        log.info("[{}] 임베딩 호출 완료. repoId={}, 소요={}ms",
            Thread.currentThread().getName(), repoId, System.currentTimeMillis() - embedStart);

        long upsertStart = System.currentTimeMillis();
        List<PineconeClient.UpsertItem> items = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            ChunkMeta meta = batch.get(i);
            String code = meta.chunk().length() > 1000 ? meta.chunk().substring(0, 1000) : meta.chunk();

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
            metadata.put("referencedTypeNames", meta.referencedTypeNames() != null ? meta.referencedTypeNames() : List.of());
            metadata.put("annotations", meta.annotations() != null ? meta.annotations() : List.of());

            items.add(new PineconeClient.UpsertItem(meta.id(), vectors.get(i), metadata));
        }

        pineconeClient.upsertBatch(items, String.valueOf(repoId));
        log.info("[{}] Pinecone upsert 완료. repoId={}, 청크수={}, 소요={}ms",
            Thread.currentThread().getName(), repoId, batch.size(), System.currentTimeMillis() - upsertStart);
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
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + repoId));
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
        Path repoPath = Paths.get(
            System.getProperty("java.io.tmpdir"), "devchat-measure", UUID.randomUUID().toString()
        );
        try {
            String token = internalAuthClient.getGithubToken(memberId);
            cloneRepo(repoUrl, token, repoPath);

            List<Path> files = collectFiles(repoPath);
            log.info("[측정] 청킹 대상 파일 수: {}", files.size());

            List<String> allChunks = new ArrayList<>();
            for (Path file : files) {
                String content = Files.readString(file);
                if (content.isBlank()) continue;
                String relativePath = repoPath.relativize(file).toString();
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
                String relativePath = repoPath.relativize(file).toString();
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