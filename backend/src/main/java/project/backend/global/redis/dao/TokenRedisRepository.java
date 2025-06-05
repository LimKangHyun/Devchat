package project.backend.global.redis.dao;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import project.backend.global.redis.entity.TokenRedis;

public interface TokenRedisRepository extends CrudRepository<TokenRedis, Long> {

	Optional<TokenRedis> findByAccessToken(String accessToken);
}
