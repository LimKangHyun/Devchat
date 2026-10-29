package project.api.domain.schedule.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.domain.schedule.dao.CalendarFeedTokenRepository;
import project.api.domain.schedule.dao.ScheduleRepository;
import project.api.domain.schedule.dto.FeedUrlResponse;
import project.api.domain.schedule.entity.CalendarFeedToken;
import project.api.global.exception.errorcode.ScheduleErrorCode;
import project.api.global.exception.ex.ScheduleException;
import project.api.global.util.CryptUtils;

@ExtendWith(MockitoExtension.class)
class CalendarFeedServiceTest {

    private static final String BASE = "https://devchat.example.com/";

    @Mock CalendarFeedTokenRepository tokenRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    CalendarFeedService service;

    // memberId -> 토큰 (가짜 저장소)
    final Map<Long, CalendarFeedToken> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        CryptUtils cryptUtils = new CryptUtils();
        ReflectionTestUtils.setField(cryptUtils, "secretKey", "test-encrypt-secret");
        service = new CalendarFeedService(tokenRepository, scheduleRepository, cryptUtils,
            redisTemplate, BASE);

        lenient().when(tokenRepository.findById(anyLong()))
            .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<Long>getArgument(0))));
        lenient().when(tokenRepository.save(any(CalendarFeedToken.class)))
            .thenAnswer(inv -> {
                CalendarFeedToken t = inv.getArgument(0);
                store.put(t.getMemberId(), t);
                return t;
            });
        lenient().when(tokenRepository.findByTokenHash(anyString()))
            .thenAnswer(inv -> store.values().stream()
                .filter(t -> t.getTokenHash().equals(inv.getArgument(0)))
                .findFirst());
    }

    private static String tokenOf(FeedUrlResponse r) {
        String url = r.httpsUrl();
        return url.substring(url.lastIndexOf('/') + 1, url.length() - ".ics".length());
    }

    @Test
    void getOrCreate_isStable() {
        FeedUrlResponse first = service.getOrCreateUrl(1L);
        FeedUrlResponse second = service.getOrCreateUrl(1L);

        assertThat(second).isEqualTo(first);
        assertThat(first.httpsUrl()).startsWith("https://devchat.example.com/api/calendar/feed/")
            .endsWith(".ics");
        // 32바이트 base64url (패딩 없음) = 43자
        assertThat(tokenOf(first)).hasSize(43).matches("[A-Za-z0-9_-]+");
        // DB에는 원문을 두지 않는다
        CalendarFeedToken saved = store.get(1L);
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(tokenOf(first));
        assertThat(saved.getTokenEncrypted()).doesNotContain(tokenOf(first));
    }

    @Test
    void regenerate_oldTokenNotFound() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.increment(anyString())).willReturn(1L);
        given(scheduleRepository.findAllVisible(1L)).willReturn(List.of());
        String oldToken = tokenOf(service.getOrCreateUrl(1L));

        FeedUrlResponse renewed = service.regenerate(1L);

        assertThat(tokenOf(renewed)).isNotEqualTo(oldToken);
        assertThat(service.getOrCreateUrl(1L)).isEqualTo(renewed);
        assertThatThrownBy(() -> service.feed(oldToken))
            .isInstanceOf(ScheduleException.class)
            .extracting(e -> ((ScheduleException) e).getErrorCode())
            .isEqualTo(ScheduleErrorCode.NOT_FOUND);
        assertThat(service.feed(tokenOf(renewed))).startsWith("BEGIN:VCALENDAR\r\n");
    }

    @Test
    void rateLimit_over120_rejected() {
        String token = tokenOf(service.getOrCreateUrl(1L));
        String key = "calendar:feed:rate:" + CalendarFeedService.hash(token);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.increment(key)).willReturn(120L, 121L);
        given(scheduleRepository.findAllVisible(1L)).willReturn(List.of());

        assertThat(service.feed(token)).contains("X-WR-CALNAME:DevChat");
        assertThatThrownBy(() -> service.feed(token))
            .isInstanceOf(ScheduleException.class)
            .extracting(e -> ((ScheduleException) e).getErrorCode())
            .isEqualTo(ScheduleErrorCode.TOO_MANY_REQUESTS);
        assertThat(ScheduleErrorCode.TOO_MANY_REQUESTS.getStatus()).isEqualTo(429);
        assertThat(ScheduleErrorCode.NOT_FOUND.getStatus()).isEqualTo(404);
    }

    @Test
    void webcalUrl_replacesScheme() {
        FeedUrlResponse r = service.getOrCreateUrl(1L);

        assertThat(r.webcalUrl())
            .isEqualTo(r.httpsUrl().replaceFirst("^https://", "webcal://"))
            .startsWith("webcal://devchat.example.com/api/calendar/feed/");
    }
}
