package project.backend.auth.dto;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import project.backend.global.exception.errorcode.AuthErrorCode;
import project.backend.global.exception.ex.AuthException;

@Slf4j
public record OAuth2Attribute(
	Map<String, Object> attributes,
	//login
	String attributeKey,
	String name,
	String email,
	String githubAccess

) {

	public static OAuth2Attribute of(String provider, String attributeKey,
		Map<String, Object> attributes) {
		return switch (provider) {
			case "github" -> ofGithub(attributeKey, attributes);
			default -> {
				log.error("provider = {}", provider);
				throw new AuthException(AuthErrorCode.UNSUPPORTED_PROVIDER);
			}
		};
	}

	private static OAuth2Attribute ofGithub(String attributeKey, Map<String, Object> attributes) {
		return new OAuth2Attribute(
			attributes,
			(String) attributes.get("login"),
			(String) attributes.get("name"),
			(String) attributes.get("email"),
			(String) attributes.get("githubAccess")
		);
	}


	public Map<String, Object> convertToMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("login", attributeKey);
		map.put("name", name);
		map.put("email", email);
		map.put("githubAccess", githubAccess);
		return map;
	}
}
