package project.api.auth.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.auth.dto.MemberDetails;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderSessionTest {

    @Test
    void tokenPair_carriesSid() {
        JwtProvider jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "SECRET_KEY", "test-secret-key");
        MemberDetails details = new MemberDetails(1L, "user1", "닉네임", null);

        Token token = jwtProvider.generateTokenPair(details, "s-1");
        Authentication auth = jwtProvider.getAuthentication(token.accessToken());

        MemberDetails principal = (MemberDetails) auth.getPrincipal();
        assertThat(principal.getSessionId()).isEqualTo("s-1");
        assertThat(principal.getId()).isEqualTo(1L);
    }

    @Test
    void tokenWithoutSid_hasNullSessionId() {
        JwtProvider jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "SECRET_KEY", "test-secret-key");
        MemberDetails details = new MemberDetails(1L, "user1", "닉네임", null);

        Token token = jwtProvider.generateTokenPair(details, null);
        MemberDetails principal =
                (MemberDetails) jwtProvider.getAuthentication(token.accessToken()).getPrincipal();

        assertThat(principal.getSessionId()).isNull();
    }
}
