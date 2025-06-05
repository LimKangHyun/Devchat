package project.backend.global.security.app;

import java.util.Collections;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.github.GitHubClient;
import project.backend.global.security.dto.CustomOAuth2User;
import project.backend.global.security.dto.OAuth2Attribute;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private final GitHubClient gitHubClient;

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService = new DefaultOAuth2UserService();

		OAuth2User oAuth2User = oAuth2UserService.loadUser(userRequest);
		String accessToken = userRequest.getAccessToken().getTokenValue();

		//public이메일 없으면 primary이메일 가져와서 사용
		String email = Optional.ofNullable((String) oAuth2User.getAttributes().get("email"))
			.orElseGet(() -> gitHubClient.getPrivateEmail(accessToken));

		String registrationId = userRequest.getClientRegistration().getRegistrationId();

		String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
			.getUserInfoEndpoint().getUserNameAttributeName();

		CustomOAuth2User customOAuth2User = new CustomOAuth2User(oAuth2User, email, accessToken);

		OAuth2Attribute oAuth2Attribute = OAuth2Attribute.of(registrationId, userNameAttributeName,
			customOAuth2User.getAttributes());

		var memberAttribute = oAuth2Attribute.convertToMap();

		return new DefaultOAuth2User(
			Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
			memberAttribute, "login");
	}
}

