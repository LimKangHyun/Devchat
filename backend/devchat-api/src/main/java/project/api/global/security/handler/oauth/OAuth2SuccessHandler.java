package project.api.global.security.handler.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import project.api.auth.app.AuthTokenService;
import project.api.global.util.CookieUtils;
import project.api.auth.app.OAuthSignUpService;
import project.api.auth.dto.MemberDetails;
import project.api.auth.dto.OAuthMemberDto;
import project.api.auth.jwt.JwtProvider;
import project.api.auth.jwt.Token;
import project.api.domain.member.app.ProfileImageCache;
import project.api.domain.member.entity.Member;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${jwt.redirection.base}")
    private String baseUrl;

    private final JwtProvider jwtProvider;
    private final OAuthSignUpService oAuthSignUpService;
    private final AuthTokenService authTokenService;
    private final ProfileImageCache profileImageCache;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        var oAuth2User = (OAuth2User) authentication.getPrincipal();

        OAuthMemberDto userDto = new OAuthMemberDto(
                (String) oAuth2User.getAttributes().get("email"),
                (String) oAuth2User.getAttributes().get("name"),
                (String) oAuth2User.getAttributes().get("login"));

        Member member = oAuthSignUpService.OAuthSignUp(userDto);

        MemberDetails memberDetails = new MemberDetails(
                member.getId(), member.getUsername(),
                member.getNickname(), member.getProfileImage());

        Token token = jwtProvider.generateTokenPair(memberDetails);

        String githubAccessToken = (String) oAuth2User.getAttributes().get("githubAccess");

        authTokenService.saveToken(member.getId(), token.refreshToken(), githubAccessToken);

        CookieUtils.saveAccessTokenCookie(response, token.accessToken());

        CookieUtils.saveRefreshTokenCookie(response, token.refreshToken());

        profileImageCache.setProfileImage(member.getId(), member.getProfileImage());

        log.info("OAuth 로그인 성공: {}", member.getUsername());

        String redirectUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .build().toUriString();

        response.sendRedirect(redirectUrl);
    }
}