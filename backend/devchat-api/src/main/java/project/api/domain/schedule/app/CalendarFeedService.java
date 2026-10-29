package project.api.domain.schedule.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.schedule.dao.CalendarFeedTokenRepository;
import project.api.domain.schedule.dao.ScheduleRepository;
import project.api.domain.schedule.dto.FeedUrlResponse;
import project.api.domain.schedule.entity.CalendarFeedToken;
import project.api.global.exception.errorcode.ScheduleErrorCode;
import project.api.global.exception.ex.ScheduleException;
import project.api.global.util.CryptUtils;

// 애플 캘린더 구독 주소(.ics). 로그인 없이 열리므로 토큰이 곧 열쇠다
@Slf4j
@Service
public class CalendarFeedService {

    static final int MAX_PER_HOUR = 120;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final String RATE_KEY = "calendar:feed:rate:";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CalendarFeedTokenRepository tokenRepository;
    private final ScheduleRepository scheduleRepository;
    private final CryptUtils cryptUtils;
    private final StringRedisTemplate redisTemplate;
    private final String baseUrl;

    public CalendarFeedService(CalendarFeedTokenRepository tokenRepository,
                               ScheduleRepository scheduleRepository,
                               CryptUtils cryptUtils,
                               StringRedisTemplate redisTemplate,
                               @Value("${jwt.redirection.base}") String baseUrl) {
        this.tokenRepository = tokenRepository;
        this.scheduleRepository = scheduleRepository;
        this.cryptUtils = cryptUtils;
        this.redisTemplate = redisTemplate;
        // 끝의 / 는 떼고 붙인다
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public FeedUrlResponse getOrCreateUrl(Long me) {
        return tokenRepository.findById(me)
            .map(t -> urlOf(cryptUtils.decrypt(t.getTokenEncrypted())))
            .orElseGet(() -> {
                String token = newToken();
                tokenRepository.save(new CalendarFeedToken(me, hash(token),
                    cryptUtils.encrypt(token), now()));
                return urlOf(token);
            });
    }

    /** 새로 만들기. 예전 주소는 바로 못 쓴다 */
    @Transactional
    public FeedUrlResponse regenerate(Long me) {
        String token = newToken();
        String hash = hash(token);
        String encrypted = cryptUtils.encrypt(token);
        tokenRepository.findById(me).ifPresentOrElse(
            t -> t.regenerate(hash, encrypted, now()),
            () -> tokenRepository.save(new CalendarFeedToken(me, hash, encrypted, now())));
        return urlOf(token);
    }

    // 참여자 닉네임·뺀 날짜를 읽으므로 트랜잭션 안에서 만든다
    @Transactional(readOnly = true)
    public String feed(String token) {
        String hash = hash(token);
        CalendarFeedToken feedToken = tokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.NOT_FOUND));
        if (!tryAcquire(hash)) {
            throw new ScheduleException(ScheduleErrorCode.TOO_MANY_REQUESTS);
        }
        return IcsWriter.write(scheduleRepository.findAllVisible(feedToken.getMemberId()), now());
    }

    // 토큰 하나당 1시간에 MAX_PER_HOUR번
    private boolean tryAcquire(String hash) {
        String key = RATE_KEY + hash;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            // 첫 요청이거나, expire가 빠져 TTL 없는 키가 남았으면 다시 건다 (안 그러면 영영 429)
            if (count != null && (count == 1L || Long.valueOf(-1L).equals(redisTemplate.getExpire(key)))) {
                redisTemplate.expire(key, WINDOW);
            }
            return count == null || count <= MAX_PER_HOUR;
        } catch (Exception e) {
            // Redis가 잠깐 안 돼도 피드는 준다
            log.warn("캘린더 피드 요청 횟수 확인 실패");
            return true;
        }
    }

    private FeedUrlResponse urlOf(String token) {
        return FeedUrlResponse.of(baseUrl + "/api/calendar/feed/" + token + ".ics");
    }

    // 32바이트 랜덤 → base64url (패딩 없음)
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 조회용 SHA-256 (hex 64자) */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }
}
