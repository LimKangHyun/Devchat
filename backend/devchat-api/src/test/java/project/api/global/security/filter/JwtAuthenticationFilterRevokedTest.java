package project.api.global.security.filter;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.auth.app.RevokedSessionStore;
import project.api.auth.dto.MemberDetails;
import project.api.auth.jwt.JwtProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterRevokedTest {

    @Mock RevokedSessionStore revokedSessionStore;

    JwtAuthenticationFilter filter;
    MockHttpServletRequest request;
    MockHttpServletResponse response;
    MockFilterChain chain;

    @BeforeEach
    void setUp() {
        JwtProvider jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "SECRET_KEY", "test-secret-key");
        filter = new JwtAuthenticationFilter(jwtProvider, revokedSessionStore);

        MemberDetails member = new MemberDetails(1L, "user1", "닉네임", "img.png", "s-1");
        String accessToken = jwtProvider.generateTokenPair(member, "s-1").accessToken();
        request = new MockHttpServletRequest("GET", "/members/me");
        request.setCookies(new Cookie("accessToken", accessToken));
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenButRevokedSession_401() throws Exception {
        given(revokedSessionStore.isRevoked("s-1")).willReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("로그아웃된 기기입니다.");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validTokenActiveSession_passes() throws Exception {
        given(revokedSessionStore.isRevoked("s-1")).willReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
