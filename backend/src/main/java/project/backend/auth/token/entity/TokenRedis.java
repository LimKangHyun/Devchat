package project.backend.auth.token.entity;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

@Getter
@AllArgsConstructor
@RedisHash(value = "token", timeToLive = 604800)
public class TokenRedis {

	@Id
	private Long id;

	@Indexed
	private String accessToken;

	private String refreshToken;

	private String githubAccess;

	public void updateAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}
}
