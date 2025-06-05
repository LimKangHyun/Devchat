package project.backend.global.security.dto;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

@RequiredArgsConstructor
public class CustomOAuth2User implements OAuth2User {

	private final OAuth2User oAuth2User;
	private final String email;
	private final String githubAccess;

	@Override
	public Map<String, Object> getAttributes() {
		Map<String, Object> extended = new HashMap<>(oAuth2User.getAttributes());
		extended.put("githubAccess", githubAccess);
		extended.put("email", email);
		return extended;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return oAuth2User.getAuthorities();
	}

	@Override
	public String getName() {
		return oAuth2User.getName();
	}
}
