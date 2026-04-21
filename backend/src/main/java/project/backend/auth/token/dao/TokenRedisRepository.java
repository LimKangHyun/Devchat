package project.backend.auth.token.dao;

import org.springframework.data.repository.CrudRepository;
import project.backend.auth.token.entity.TokenRedis;

public interface TokenRedisRepository extends CrudRepository<TokenRedis, Long> {

}
