package project.ai.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;

/**
 * 리스너가 정상으로 끝나면 ACK한다. 예외를 던지면 ACK하지 않고 그대로 던져, 메시지가 PEL에 남아
 * PendingMessageReclaimer가 나중에 다시 처리하게 한다.
 *
 * 그래서 감싸는 리스너는 "처리했다(건너뛴 것 포함)"면 정상 리턴하고, "다시 시도해야 한다"면 예외를 던진다.
 * 다시 시도해도 계속 실패하는 메시지(역직렬화 실패 등)는 리스너가 로그만 남기고 정상 리턴해 ACK되게 한다.
 */
@Slf4j
public class AckOnSuccessListener implements StreamListener<String, ObjectRecord<String, String>> {

    private final StreamListener<String, ObjectRecord<String, String>> delegate;
    private final StringRedisTemplate redis;
    private final String streamKey;
    private final String group;

    public AckOnSuccessListener(StreamListener<String, ObjectRecord<String, String>> delegate,
                                StringRedisTemplate redis, String streamKey, String group) {
        this.delegate = delegate;
        this.redis = redis;
        this.streamKey = streamKey;
        this.group = group;
    }

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        delegate.onMessage(record);
        try {
            redis.opsForStream().acknowledge(streamKey, group, record.getId());
        } catch (Exception e) {
            // 처리는 끝났다. PEL에 남으면 다시 처리될 수 있는데, 스트림마다 다시 처리해도 안전하게 만들어 두었다
            log.warn("ACK 실패, 다시 처리될 수 있음 - stream={}, recordId={}", streamKey, record.getId(), e);
        }
    }
}
