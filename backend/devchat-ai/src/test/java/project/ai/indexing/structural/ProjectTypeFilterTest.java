package project.ai.indexing.structural;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTypeFilterTest {

    private static final String OWN = "org.springframework.security.oauth2.server.authorization.authentication";

    @Test
    void 리뷰_파일과_패키지_앞부분이_같은_import는_프로젝트_타입이다() {
        List<String> imports = List.of("org.springframework.security.oauth2.server.authorization.OAuth2Authorization");
        assertThat(ProjectTypeFilter.isProjectType("OAuth2Authorization", imports, OWN)).isTrue();
    }

    @Test
    void 같은_회사의_다른_라이브러리나_외부_라이브러리는_프로젝트_타입이_아니다() {
        List<String> imports = List.of("org.springframework.util.Assert", "com.nimbusds.jose.jwk.JWKSet");
        assertThat(ProjectTypeFilter.isProjectType("Assert", imports, OWN)).isFalse();
        assertThat(ProjectTypeFilter.isProjectType("JWKSet", imports, OWN)).isFalse();
    }

    @Test
    void 패키지_이름과_무관하게_동작한다() {
        assertThat(ProjectTypeFilter.isProjectType("Order",
            List.of("com.example.shop.order.Order"), "com.example.shop.payment")).isTrue();
        assertThat(ProjectTypeFilter.isProjectType("Money",
            List.of("com.other.lib.Money"), "com.example.shop.payment")).isFalse();
    }

    @Test
    void 도메인_역순이_아닌_패키지는_첫_단계가_같으면_같은_저장소다() {
        // DevChat처럼 project.ai, project.common으로 모듈이 갈리는 경우
        assertThat(ProjectTypeFilter.isProjectType("ChatMessage",
            List.of("project.common.dto.ChatMessage"), "project.ai.rag")).isTrue();
    }

    @Test
    void 내부_클래스는_바깥_클래스_import로도_판단한다() {
        List<String> imports = List.of("org.springframework.security.oauth2.server.authorization.OAuth2Authorization");
        assertThat(ProjectTypeFilter.isProjectType("OAuth2Authorization.Token", imports, OWN)).isTrue();
    }

    @Test
    void import에_없으면_같은_패키지_타입으로_보되_java_lang은_뺀다() {
        assertThat(ProjectTypeFilter.isProjectType("TokenSettings", List.of(), OWN)).isTrue();
        assertThat(ProjectTypeFilter.isProjectType("String", List.of(), OWN)).isFalse();
    }
}
