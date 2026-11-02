package project.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class StreamPollTimeoutTest {

    private static final Duration TWO_SECONDS = Duration.ofSeconds(2);

    @Test
    void 명령_시간_제한이_충분히_길면_그대로_쓴다() {
        assertThat(StreamPollTimeout.safe(TWO_SECONDS, Optional.of(Duration.ofSeconds(5)))).isEqualTo(TWO_SECONDS);
        assertThat(StreamPollTimeout.safe(TWO_SECONDS, Optional.of(Duration.ofMillis(2200)))).isEqualTo(TWO_SECONDS);
    }

    @Test
    void 운영의_예전_값_500ms이면_여유_200ms를_빼고_300ms로_줄인다() {
        assertThat(StreamPollTimeout.safe(TWO_SECONDS, Optional.of(Duration.ofMillis(500))))
            .isEqualTo(Duration.ofMillis(300));
    }

    @Test
    void 여유가_조금_모자라도_줄인다() {
        assertThat(StreamPollTimeout.safe(TWO_SECONDS, Optional.of(Duration.ofMillis(2100))))
            .isEqualTo(Duration.ofMillis(1900));
        assertThat(StreamPollTimeout.safe(TWO_SECONDS, Optional.of(TWO_SECONDS)))
            .isEqualTo(Duration.ofMillis(1800));
    }

    @Test
    void 명령_시간_제한이_아주_짧으면_절반을_쓰고_0은_되지_않는다() {
        assertThat(StreamPollTimeout.safe(TWO_SECONDS, Optional.of(Duration.ofMillis(250))))
            .isEqualTo(Duration.ofMillis(125));
        assertThat(StreamPollTimeout.safe(TWO_SECONDS, Optional.of(Duration.ofMillis(1))))
            .isEqualTo(Duration.ofMillis(1));
        assertThat(StreamPollTimeout.safe(TWO_SECONDS, Optional.of(Duration.ZERO)))
            .isEqualTo(Duration.ofMillis(1));
    }

    @Test
    void 명령_시간_제한을_모르면_그대로_쓴다() {
        assertThat(StreamPollTimeout.safe(TWO_SECONDS, Optional.empty())).isEqualTo(TWO_SECONDS);
    }

    @Test
    void Lettuce_연결_팩토리에서_명령_시간_제한을_읽는다() {
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());

        assertThat(StreamPollTimeout.commandTimeoutOf(factory)).contains(Duration.ofMillis(500));
    }
}
