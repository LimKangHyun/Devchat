package project.api.domain.aireview.stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import project.api.domain.aireview.app.IndexingStatusUpdater;
import project.api.domain.chat.chatroom.app.IndexingStatusBroadcaster;
import project.api.domain.chat.chatroom.dto.IndexingStatusResponse;
import project.common.enums.IndexingStatus;

@ExtendWith(MockitoExtension.class)
class RepoIndexResultConsumerTest {

    @Mock private IndexingStatusUpdater indexingStatusUpdater;
    @Mock private IndexingStatusBroadcaster broadcaster;

    private RepoIndexResultConsumer consumer() {
        return new RepoIndexResultConsumer(new ObjectMapper(), indexingStatusUpdater, broadcaster);
    }

    private static ObjectRecord<String, String> record(String json) {
        return StreamRecords.newRecord().ofObject(json).withStreamKey("stream:repo-index:result");
    }

    @Test
    @DisplayName("취소 결과를 받으면 상태를 NONE으로 되돌리고 화면에는 CANCELLED로 알린다")
    void cancelled() {
        given(indexingStatusUpdater.applyResult(3L, IndexingStatus.NONE)).willReturn(true);

        consumer().onMessage(record("{\"roomId\":3,\"success\":false,\"errorMessage\":null,\"cancelled\":true}"));

        then(broadcaster).should().send(3L, IndexingStatusResponse.finished("CANCELLED"));
    }

    @Test
    @DisplayName("성공 결과를 받으면 COMPLETED로 바꾸고 알린다")
    void success() {
        given(indexingStatusUpdater.applyResult(3L, IndexingStatus.COMPLETED)).willReturn(true);

        consumer().onMessage(record("{\"roomId\":3,\"success\":true,\"errorMessage\":null,\"cancelled\":false}"));

        then(broadcaster).should().send(3L, IndexingStatusResponse.finished("COMPLETED"));
    }

    @Test
    @DisplayName("이미 다른 상태라 반영하지 않은 결과(늦게 도착한 재처리 등)는 화면에 알리지 않는다")
    void notApplied_noBroadcast() {
        given(indexingStatusUpdater.applyResult(3L, IndexingStatus.NONE)).willReturn(false);

        consumer().onMessage(record("{\"roomId\":3,\"success\":false,\"errorMessage\":null,\"cancelled\":true}"));

        then(broadcaster).should(never()).send(anyLong(), any());
    }

    @Test
    @DisplayName("cancelled 필드가 없는 이전 형식의 실패 메시지는 FAILED로 처리한다")
    void legacyFailure() {
        given(indexingStatusUpdater.applyResult(3L, IndexingStatus.FAILED)).willReturn(true);

        consumer().onMessage(record("{\"roomId\":3,\"success\":false,\"errorMessage\":\"boom\"}"));

        then(broadcaster).should().send(3L, IndexingStatusResponse.finished("FAILED"));
    }

    @Test
    @DisplayName("처리 중 오류는 예외를 던져 ACK를 막고, 역직렬화 실패는 폐기한다")
    void errorHandling() {
        given(indexingStatusUpdater.applyResult(3L, IndexingStatus.COMPLETED)).willThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> consumer().onMessage(
            record("{\"roomId\":3,\"success\":true,\"errorMessage\":null,\"cancelled\":false}")))
            .isInstanceOf(IllegalStateException.class);
        consumer().onMessage(record("not-json")); // 예외 없이 끝나야 한다
    }
}
