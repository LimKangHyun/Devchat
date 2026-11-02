package project.ai.stream.index;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import project.ai.indexing.RepoIndexingService;
import project.common.message.index.IndexingKeys;
import project.common.message.index.RepoIndexRequestMessage;

class RepoIndexConsumerTest {

    private static final long ROOM = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RepoIndexingService indexing;
    private StringRedisTemplate redis;
    private RepoIndexResultProducer resultProducer;
    private RepoIndexConsumer consumer;

    @BeforeEach
    void setUp() {
        indexing = mock(RepoIndexingService.class);
        redis = mock(StringRedisTemplate.class);
        resultProducer = mock(RepoIndexResultProducer.class);
        consumer = new RepoIndexConsumer(objectMapper, indexing, redis, resultProducer);
    }

    private ObjectRecord<String, String> record() throws Exception {
        String json = objectMapper.writeValueAsString(new RepoIndexRequestMessage(ROOM, "https://github.com/a/b", 1L));
        return StreamRecords.newRecord().in(RepoIndexConsumer.STREAM_KEY).withId(RecordId.of("1-0")).ofObject(json);
    }

    private void keys(boolean lock, boolean cancel, boolean requested) {
        when(redis.hasKey(IndexingKeys.lockKey(ROOM))).thenReturn(lock);
        when(redis.hasKey(IndexingKeys.cancelKey(ROOM))).thenReturn(cancel);
        when(redis.hasKey(IndexingKeys.requestedKey(ROOM))).thenReturn(requested);
    }

    @Test
    void 재처리인데_요청_표시가_없으면_끝났거나_만료된_요청이라_인덱싱하지_않는다() throws Exception {
        keys(false, false, false);

        consumer.onReclaimed(record());

        verify(indexing, never()).indexRepository(anyLong(), anyString(), anyLong());
    }

    @Test
    void 재처리인데_요청_표시가_남아_있으면_인덱싱한다() throws Exception {
        keys(false, false, true);

        consumer.onReclaimed(record());

        verify(indexing).indexRepository(ROOM, "https://github.com/a/b", 1L);
    }

    @Test
    void 처음_전달된_요청은_요청_표시가_만료됐어도_인덱싱한다() throws Exception {
        // AI 서버가 오래 내려가 있다 돌아와 밀린 요청을 처리하는 경우. API 서버는 끝나면 COMPLETED로 바꾼다
        keys(false, false, false);

        consumer.onMessage(record());

        verify(indexing).indexRepository(ROOM, "https://github.com/a/b", 1L);
    }

    @Test
    void 취소되거나_삭제된_방의_요청은_인덱싱하지_않고_취소됨을_알린다() throws Exception {
        keys(false, true, true);

        consumer.onReclaimed(record());

        verify(indexing, never()).indexRepository(anyLong(), anyString(), anyLong());
        verify(resultProducer).publishCancelled(ROOM);
        verify(redis).delete(IndexingKeys.requestedKey(ROOM));
    }

    @Test
    void 이미_인덱싱_중이면_건너뛴다() throws Exception {
        keys(true, false, true);

        consumer.onMessage(record());

        verify(indexing, never()).indexRepository(anyLong(), anyString(), anyLong());
    }

    @Test
    void 실행기에_넘기지_못하면_예외를_던져_ACK되지_않게_한다() throws Exception {
        keys(false, false, true);
        doThrow(new TaskRejectedException("가득 참")).when(indexing).indexRepository(anyLong(), anyString(), anyLong());

        assertThatThrownBy(() -> consumer.onMessage(record())).isInstanceOf(TaskRejectedException.class);
    }

    @Test
    void 역직렬화에_실패하면_버린다() {
        ObjectRecord<String, String> broken = StreamRecords.newRecord()
            .in(RepoIndexConsumer.STREAM_KEY).withId(RecordId.of("1-0")).ofObject("{not json");

        consumer.onMessage(broken);

        verify(indexing, never()).indexRepository(anyLong(), anyString(), anyLong());
    }
}
