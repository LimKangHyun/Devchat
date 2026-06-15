package project.backend.domain.aireview.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import project.backend.domain.aireview.client.PineconeClient;
import project.backend.domain.aireview.dto.ChunkMeta;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.IndexingStatus;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepoIndexingService {

    private static final String LOCK_PREFIX = "repo:indexing:";
    private static final String CANCEL_PREFIX = "repo:indexing:cancel:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final int MAX_WAIT_SECONDS = 60;
    private static final long POLL_INTERVAL_MS = 500;
    private static final int BATCH_SIZE = 20;

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "node_modules", ".git", "build", "out", "target", ".gradle"
    );

    private final EmbeddingService embeddingService;
    private final PineconeClient pineconeClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final IndexingStatusUpdater indexingStatusUpdater;

    private final Semaphore semaphore = new Semaphore(4);

    @Async("repoIndexingExecutor")
    public void indexRepository(Long repoId, String repoUrl, String token) {
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
            indexingStatusUpdater.update(repoId, IndexingStatus.RUNNING);

            cloneRepo(repoUrl, token, repoPath);
            processAndIndex(repoId, repoPath);

            indexingStatusUpdater.update(repoId, IndexingStatus.COMPLETED);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("레포 인덱싱 완료. repoId={}, 소요시간={}ms ({}초)", repoId, elapsed, elapsed / 1000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("레포 인덱싱 인터럽트. repoId={}", repoId, e);
            indexingStatusUpdater.update(repoId, IndexingStatus.FAILED);
        } catch (Exception e) {
            log.error("레포 인덱싱 실패. repoId={}", repoId, e);
            indexingStatusUpdater.update(repoId, IndexingStatus.FAILED);
        } finally {
            semaphore.release();
            deleteDirectory(repoPath);
            redisTemplate.delete(lockKey);
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
            throw new IOException("git clone 실패. repoId 확인 필요. exitCode=" + exitCode);
        }
    }

    private void processAndIndex(Long repoId, Path repoPath) throws IOException {
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
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        int totalFiles = javaFiles.size();
        int indexedFiles = 0;

        log.info("인덱싱 대상 파일 수: {}. repoId={}", totalFiles, repoId);
        broadcastIndexingStatus(repoId, totalFiles, indexedFiles, "RUNNING");

        List<ChunkMeta> buffer = new ArrayList<>();

        for (Path file : javaFiles) {
            if (isCancelled(repoId)) {
                log.info("인덱싱 취소 감지. repoId={}", repoId);
                indexingStatusUpdater.update(repoId, IndexingStatus.FAILED);
                return;
            }

            try {
                String content = Files.readString(file);
                if (content.isBlank()) continue;

                String relativePath = repoPath.relativize(file).toString();
                List<String> chunks = chunk(content);

                for (int i = 0; i < chunks.size(); i++) {
                    String id = repoId + "-" + relativePath.replace("/", "_") + "-" + i;
                    buffer.add(new ChunkMeta(id, relativePath, i, chunks.get(i)));
                }

                // 버퍼가 BATCH_SIZE 이상이면 flush
                while (buffer.size() >= BATCH_SIZE) {
                    flushBatch(repoId, buffer.subList(0, BATCH_SIZE));
                    buffer = new ArrayList<>(buffer.subList(BATCH_SIZE, buffer.size()));
                }

                indexedFiles++;
                broadcastIndexingStatus(repoId, totalFiles, indexedFiles, "RUNNING");

            } catch (Exception e) {
                log.warn("파일 청킹/임베딩 실패. file={}", file, e);
            }
        }

        // 남은 버퍼 flush
        if (!buffer.isEmpty()) {
            flushBatch(repoId, buffer);
        }

        broadcastIndexingStatus(repoId, totalFiles, indexedFiles, "COMPLETED");
        log.info("인덱싱 완료. repoId={}, 총 API 호출 횟수={}", repoId, embeddingService.getAndResetCount());
    }

    private void flushBatch(Long repoId, List<ChunkMeta> batch) {
        List<String> texts = batch.stream().map(ChunkMeta::chunk).toList();
        List<float[]> vectors = embeddingService.embedBatch(texts);

        for (int i = 0; i < batch.size(); i++) {
            ChunkMeta meta = batch.get(i);
            String code = meta.chunk().length() > 1000 ? meta.chunk().substring(0, 1000) : meta.chunk();

            Map<String, String> metadata = Map.of(
                    "repoId", String.valueOf(repoId),
                    "filePath", meta.relativePath(),
                    "chunkIndex", String.valueOf(meta.chunkIndex()),
                    "code", code,
                    "language", "java"
            );

            pineconeClient.upsert(meta.id(), vectors.get(i), metadata, String.valueOf(repoId));
        }
    }

    private void broadcastIndexingStatus(Long repoId, int totalFiles, int indexedFiles, String status) {
        messagingTemplate.convertAndSend(
                "/topic/chat/" + repoId,
                Map.of(
                        "type", "INDEXING_PROGRESS",
                        "totalFiles", totalFiles,
                        "indexedFiles", indexedFiles,
                        "status", status
                )
        );
    }

    private List<String> chunk(String content) {
        List<String> chunks = extractMethods(content);
        if (!chunks.isEmpty()) return chunks;
        return slidingWindow(content, 100, 10);
    }

    private List<String> extractMethods(String content) {
        List<String> methods = new ArrayList<>();
        String[] lines = content.split("\n");
        int depth = 0;
        int methodStart = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean isMethodSignature = depth == 1
                    && (line.contains("public ") || line.contains("private ") || line.contains("protected "))
                    && line.contains("(") && !line.contains("class ") && !line.contains("interface ");

            if (isMethodSignature && line.contains("{")) {
                methodStart = i;
            }

            for (char c : line.toCharArray()) {
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }

            if (methodStart != -1 && depth == 1) {
                String method = String.join("\n", Arrays.copyOfRange(lines, methodStart, i + 1));
                if (method.split("\n").length >= 3) {
                    methods.add(method);
                }
                methodStart = -1;
            }
        }
        return methods;
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
}