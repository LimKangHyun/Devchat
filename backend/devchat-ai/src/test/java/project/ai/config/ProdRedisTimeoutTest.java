package project.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import project.ai.stream.StreamPollTimeout;

/**
 * 운영 프로필의 Redis 명령 시간 제한이 스트림 폴링 대기보다 여유 있게 긴지 본다.
 * 기본 application.yml은 10초라 다른 테스트로는 드러나지 않는다. 둘 중 하나를 바꾸면 여기서 잡힌다.
 */
class ProdRedisTimeoutTest {

    @Test
    void 운영_Redis_명령_시간_제한이_스트림_폴링_대기보다_길다() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
            .load("prod", new ClassPathResource("application-prod.yml"));
        Object raw = sources.get(0).getProperty("spring.data.redis.timeout");
        assertThat(raw).as("application-prod.yml에 spring.data.redis.timeout이 있어야 한다").isNotNull();

        Duration commandTimeout = DurationStyle.detectAndParse(raw.toString());

        assertThat(commandTimeout).isGreaterThan(RedisStreamConfig.POLL_TIMEOUT);
        // 여유까지 포함해 줄이지 않아도 되는 값이어야 한다
        assertThat(StreamPollTimeout.safe(RedisStreamConfig.POLL_TIMEOUT, Optional.of(commandTimeout)))
            .isEqualTo(RedisStreamConfig.POLL_TIMEOUT);
    }
}
