package project.ai.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 컨슈머 그룹을 만든다. 이미 있으면 그대로 둔다.
 * 시작할 때(RedisStreamConfig)와, 읽다가 그룹이 없다는 오류(NOGROUP)가 났을 때(RateLimitedStreamErrorHandler) 쓴다.
 *
 * 0부터 읽는 그룹으로 만든다. redis-stream이 데이터 없이 다시 뜨면 스트림도 비어 있으므로,
 * 다시 뜬 뒤 들어온 요청부터 처리하게 된다.
 */
@Slf4j
@RequiredArgsConstructor
public class StreamConsumerGroups {

    private final StringRedisTemplate stringRedisTemplate;

    public void createIfNotExists(String streamKey, String groupName) {
        try {
            stringRedisTemplate.opsForStream()
                    .createGroup(streamKey, ReadOffset.from("0"), groupName);
            log.info("Consumer group created: {} on stream {}", groupName, streamKey);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.info("Consumer group already exists: {}", groupName);
            } else {
                log.error("Consumer group 생성 실패: streamKey={}, group={}", streamKey, groupName, e);
            }
        }
    }
}
