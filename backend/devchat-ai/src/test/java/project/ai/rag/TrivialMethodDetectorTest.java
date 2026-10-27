package project.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import project.ai.config.JavaParserConfig;

class TrivialMethodDetectorTest {

    private final TrivialMethodDetector detector = new TrivialMethodDetector(new JavaParserConfig().javaParser());

    @Test
    void 필드를_꺼내기만_하는_getter는_단순하다() {
        assertThat(detector.isTrivial("""
            /** id */
            public String getId() {
                return this.id;
            }
            """)).isTrue();
    }

    @Test
    void 상수만_넘기는_위임은_단순하다() {
        assertThat(detector.isTrivial("""
            default RegisteredClient getRegisteredClient() {
                return get(RegisteredClient.class);
            }
            """)).isTrue();
        assertThat(detector.isTrivial("""
            public String getPushedAuthorizationRequestEndpoint() {
                return getSetting(ConfigurationSettingNames.AuthorizationServer.PUSHED_AUTHORIZATION_REQUEST_ENDPOINT);
            }
            """)).isTrue();
    }

    @Test
    void 몸통이_없는_선언은_단순하다() {
        assertThat(detector.isTrivial("""
            /**
             * Saves the authorization.
             */
            void save(OAuth2Authorization authorization);
            """)).isTrue();
    }

    @Test
    void setter는_단순하지_않다() {
        assertThat(detector.isTrivial("""
            public void setSessionAuthenticationStrategy(SessionAuthenticationStrategy strategy) {
                this.sessionAuthenticationStrategy = strategy;
            }
            """)).isFalse();
    }

    @Test
    void 조건을_계산하는_return은_단순하지_않다() {
        assertThat(detector.isTrivial("""
            public boolean isActive() {
                return !isInvalidated() && !isExpired() && !isBeforeUse();
            }
            """)).isFalse();
    }

    @Test
    void 이름이_get이어도_로직이_있으면_단순하지_않다() {
        assertThat(detector.isTrivial("""
            static AuthorizationServerSettings getAuthorizationServerSettings(HttpSecurity httpSecurity) {
                AuthorizationServerSettings settings = httpSecurity.getSharedObject(AuthorizationServerSettings.class);
                if (settings == null) {
                    settings = getBean(httpSecurity, AuthorizationServerSettings.class);
                }
                return settings;
            }
            """)).isFalse();
    }
}
