package project.api.domain.aireview.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.api.domain.aireview.app.IndexingStatusUpdater;
import project.api.domain.chat.chatroom.app.IndexingStatusBroadcaster;
import project.api.domain.chat.chatroom.dto.IndexingStatusResponse;
import project.common.enums.IndexingStatus;
import project.common.message.index.RepoIndexResultMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepoIndexResultConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final IndexingStatusUpdater indexingStatusUpdater;
    private final IndexingStatusBroadcaster broadcaster;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        // 역직렬화 실패는 재시도해도 계속 실패하므로(포이즌 메시지) 로그만 남기고 정상 리턴해 ACK되게 한다.
        RepoIndexResultMessage message;
        try {
            message = objectMapper.readValue(record.getValue(), RepoIndexResultMessage.class);
        } catch (Exception e) {
            log.error("레포 인덱싱 결과 역직렬화 실패, 메시지 폐기: raw={}", record.getValue(), e);
            return;
        }

        log.info("레포 인덱싱 결과 수신: roomId={}, success={}, cancelled={}",
                message.roomId(), message.success(), message.cancelled());

        // 취소되면 청크가 모두 지워진 상태라 "인덱싱 안 됨"(NONE)으로 되돌린다. 화면에는 CANCELLED로 알린다.
        IndexingStatus status = message.success() ? IndexingStatus.COMPLETED
                : message.cancelled() ? IndexingStatus.NONE
                : IndexingStatus.FAILED;

        // 처리 실패(DB 일시 장애 등)는 예외를 그대로 던져 ACK를 막고 재처리되게 한다.
        boolean applied = indexingStatusUpdater.applyResult(message.roomId(), status);
        if (!applied) {
            log.info("이미 다른 상태라 인덱싱 결과를 반영하지 않음(늦게 도착한 결과). roomId={}, result={}",
                    message.roomId(), status);
            return;
        }

        String shown = message.cancelled() ? "CANCELLED" : status.name();
        broadcaster.send(message.roomId(), IndexingStatusResponse.finished(shown));

        if (!message.success() && !message.cancelled()) {
            log.warn("레포 인덱싱 실패: roomId={}, error={}", message.roomId(), message.errorMessage());
        }
    }
}
