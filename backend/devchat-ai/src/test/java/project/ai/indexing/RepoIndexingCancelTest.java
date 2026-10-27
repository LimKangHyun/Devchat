package project.ai.indexing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import project.ai.client.QdrantClient;
import project.ai.client.internal.InternalAuthClient;
import project.ai.indexing.chunking.AstChunkExtractor;
import project.ai.stream.index.RepoIndexResultProducer;
import project.common.message.index.IndexingKeys;

class RepoIndexingCancelTest {

    private static final long ROOM_ID = 7L;

    @TempDir Path repo;

    private ExecutorService executor;
    private EmbeddingService embeddingService;
    private QdrantClient qdrantClient;
    private StringRedisTemplate redisTemplate;
    private RepoIndexingService service;
    private List<Path> files;

    @BeforeEach
    void setUp() throws Exception {
        executor = Executors.newFixedThreadPool(2);
        embeddingService = mock(EmbeddingService.class);
        qdrantClient = mock(QdrantClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        AstChunkExtractor astChunkExtractor = mock(AstChunkExtractor.class);
        when(astChunkExtractor.extract(anyString(), anyString())).thenReturn(List.of()); // 슬라이딩 윈도우로 청킹
        when(embeddingService.embedDocuments(anyList()))
            .thenAnswer(inv -> ((List<?>) inv.getArgument(0)).stream().map(t -> new float[]{0.1f}).toList());

        service = new RepoIndexingService(executor, embeddingService, mock(SparseEncoder.class), qdrantClient,
            redisTemplate, mock(InternalAuthClient.class), mock(RepoIndexResultProducer.class),
            astChunkExtractor, mock(IndexingProgressReporter.class));

        // 슬라이딩 윈도우는 10줄 미만이면 청크를 만들지 않으므로 충분히 길게 쓴다.
        String body = "    int x = 1;\n".repeat(15);
        Files.writeString(repo.resolve("A.java"), "class A {\n" + body + "}\n");
        Files.writeString(repo.resolve("B.java"), "class B {\n" + body + "}\n");
        files = List.of(repo.resolve("A.java"), repo.resolve("B.java"));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 청킹_중에_취소되면_아무것도_저장하지_않고_취소_예외를_던진다() {
        when(redisTemplate.hasKey(IndexingKeys.cancelKey(ROOM_ID))).thenReturn(true);

        assertThatThrownBy(() -> service.indexLocalFiles(ROOM_ID, repo, files))
            .isInstanceOf(IndexingCancelledException.class);
        verify(embeddingService, never()).embedDocuments(anyList());
        verify(qdrantClient, never()).upsert(any());
    }

    @Test
    void 저장_직전에_취소되면_배치를_저장하지_않고_모든_배치가_끝난_뒤_취소_예외를_던진다() {
        // 청킹 단계(파일 2개)에서는 취소 전이고, 배치를 저장하기 직전부터 취소된 상태
        AtomicInteger calls = new AtomicInteger();
        when(redisTemplate.hasKey(IndexingKeys.cancelKey(ROOM_ID))).thenAnswer(inv -> calls.incrementAndGet() > 2);

        assertThatThrownBy(() -> service.indexLocalFiles(ROOM_ID, repo, files))
            .isInstanceOf(IndexingCancelledException.class);
        // 청킹은 끝났지만(파일 2개 확인) 배치는 저장 직전에 멈췄다
        org.assertj.core.api.Assertions.assertThat(calls.get()).isGreaterThan(2);
        verify(embeddingService, never()).embedDocuments(anyList());
        verify(qdrantClient, never()).upsert(any());
    }

    @Test
    void 취소되지_않으면_정상적으로_저장한다() {
        when(redisTemplate.hasKey(IndexingKeys.cancelKey(ROOM_ID))).thenReturn(false);

        assertThatCode(() -> service.indexLocalFiles(ROOM_ID, repo, files)).doesNotThrowAnyException();
        verify(qdrantClient).upsert(any());
    }
}
