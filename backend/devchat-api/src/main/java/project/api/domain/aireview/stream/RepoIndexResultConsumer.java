package project.api.domain.aireview.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.api.domain.aireview.app.IndexingStatusUpdater;
import project.common.enums.IndexingStatus;
import project.common.message.index.RepoIndexResultMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepoIndexResultConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final IndexingStatusUpdater indexingStatusUpdater;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        try {
            RepoIndexResultMessage message =
                    objectMapper.readValue(record.getValue(), RepoIndexResultMessage.class);

            log.info("레포 인덱싱 결과 수신: roomId={}, success={}", message.roomId(), message.success());

            IndexingStatus status = message.success() ? IndexingStatus.COMPLETED : IndexingStatus.FAILED;
            indexingStatusUpdater.update(message.roomId(), status);

            if (!message.success()) {
                log.warn("레포 인덱싱 실패: roomId={}, error={}", message.roomId(), message.errorMessage());
            }
        } catch (Exception e) {
            log.error("레포 인덱싱 결과 처리 실패: {}", e.getMessage(), e);
        }
    }
}