package project.backend.domain.member.app;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MemberRedisService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String MEMBER_PROFILE_KEY = "member:%d:profile";
    private static final long PROFILE_TTL_SEC = 60 * 60 * 24 * 7L; // 7일

    public void setProfileImage(Long memberId, String profileImg) {
        String key = String.format(MEMBER_PROFILE_KEY, memberId);
        redisTemplate.opsForValue().set(key, profileImg, PROFILE_TTL_SEC, TimeUnit.SECONDS);
    }

    public String getProfileImage(Long memberId) {
        String key = String.format(MEMBER_PROFILE_KEY, memberId);
        return redisTemplate.opsForValue().get(key);
    }
}
