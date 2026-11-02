package project.ai.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 내부 API 주소가 잘못되면 요청 때가 아니라 서버가 뜰 때 실패하는지 본다.
 * 운영에서 INTERNAL_API_URL=http://nginx_proxy/internal-api 로 떠서, 인덱싱 요청마다 "Host is not specified"로 실패했다.
 */
class InternalApiPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(Config.class);

    @Configuration
    @EnableConfigurationProperties(InternalApiProperties.class)
    static class Config {
    }

    @Test
    void 밑줄이_있는_호스트는_Java_URI가_호스트로_읽지_못한다() {
        // 고치려는 원인 자체. 이 전제가 바뀌면 검증도 다시 봐야 한다
        assertThat(URI.create("http://nginx_proxy/internal-api").getHost()).isNull();
    }

    @Test
    void 밑줄이_있는_호스트면_이유와_변수_이름을_알려_주며_실패한다() {
        assertThatThrownBy(() -> InternalApiProperties.validate("http://nginx_proxy/x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INTERNAL_API_URL")
            .hasMessageContaining("'_'")
            .hasMessageContaining("http://nginx_proxy/x");
    }

    @Test
    void 비어_있으면_실패한다() {
        assertThatThrownBy(() -> InternalApiProperties.validate(""))
            .hasMessageContaining("INTERNAL_API_URL")
            .hasMessageContaining("비어");
        assertThatThrownBy(() -> InternalApiProperties.validate(null))
            .hasMessageContaining("비어");
        // 환경 변수가 없어 자리표시자가 그대로 남은 경우
        assertThatThrownBy(() -> InternalApiProperties.validate("${INTERNAL_API_URL}"))
            .hasMessageContaining("비어");
    }

    @Test
    void 스킴이_없으면_실패한다() {
        assertThatThrownBy(() -> InternalApiProperties.validate("nginx-proxy:8088/internal-api"))
            .hasMessageContaining("http://");
        assertThatThrownBy(() -> InternalApiProperties.validate("ftp://nginx-proxy/internal-api"))
            .hasMessageContaining("http://");
    }

    @Test
    void 올바른_주소는_그대로_쓰고_끝의_슬래시만_뗀다() {
        assertThat(InternalApiProperties.validate("http://nginx-proxy:8088/internal-api"))
            .isEqualTo("http://nginx-proxy:8088/internal-api");
        assertThat(InternalApiProperties.validate("http://dev-chat-backend-green:8080/"))
            .isEqualTo("http://dev-chat-backend-green:8080");
        assertThat(new InternalApiProperties("http://nginx-proxy:8088/internal-api").host())
            .isEqualTo("nginx-proxy:8088");
    }

    @Test
    void 잘못된_주소면_컨텍스트가_뜨지_않는다() {
        runner.withPropertyValues("internal.api.url=http://nginx_proxy/internal-api")
            .run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("INTERNAL_API_URL"));
    }

    @Test
    void 값이_없으면_컨텍스트가_뜨지_않는다() {
        runner.run(context -> assertThat(context).hasFailed()
            .getFailure().rootCause().hasMessageContaining("INTERNAL_API_URL"));
    }

    @Test
    void 올바른_주소면_컨텍스트가_뜬다() {
        runner.withPropertyValues("internal.api.url=http://nginx-proxy:8088/internal-api")
            .run(context -> assertThat(context.getBean(InternalApiProperties.class).url())
                .isEqualTo("http://nginx-proxy:8088/internal-api"));
    }
}
