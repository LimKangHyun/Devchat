package project.api.domain.schedule.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.api.auth.app.RevokedSessionStore;
import project.api.auth.jwt.JwtProvider;
import project.api.domain.member.app.MemberService;
import project.api.domain.schedule.app.CalendarFeedService;
import project.api.global.exception.GlobalExceptionHandler;
import project.api.global.exception.errorcode.ScheduleErrorCode;
import project.api.global.exception.ex.ScheduleException;
import project.api.global.security.filter.JwtAuthenticationFilter;

// 실제 JwtAuthenticationFilter를 앞에 붙인 MockMvc. 쿠키 없는 애플 캘린더 요청이 막히지 않는지 본다
@ExtendWith(MockitoExtension.class)
class CalendarFeedControllerTest {

    @Mock CalendarFeedService feedService;
    @Mock MemberService memberService;
    @Mock RevokedSessionStore revokedSessionStore;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        JwtProvider jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "SECRET_KEY", "test-secret-key");
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, revokedSessionStore);
        mvc = MockMvcBuilders
            .standaloneSetup(new CalendarFeedController(feedService, memberService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(filter)
            .build();
    }

    @Test
    void feed_withoutCookie_returnsCalendar() throws Exception {
        given(feedService.feed("abc_-123")).willReturn("BEGIN:VCALENDAR\r\nX-WR-CALNAME:DevChat\r\nSUMMARY:회의\r\nEND:VCALENDAR\r\n");

        MvcResult result = mvc.perform(get("/calendar/feed/abc_-123.ics"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/calendar"))
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andReturn();

        assertThat(result.getResponse().getContentType()).containsIgnoringCase("charset=utf-8");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .contains("SUMMARY:회의");
    }

    @Test
    void feed_wrongToken_404_overLimit_429() throws Exception {
        given(feedService.feed("bad")).willThrow(new ScheduleException(ScheduleErrorCode.NOT_FOUND));
        given(feedService.feed("busy"))
            .willThrow(new ScheduleException(ScheduleErrorCode.TOO_MANY_REQUESTS));

        mvc.perform(get("/calendar/feed/bad.ics")).andExpect(status().isNotFound());
        mvc.perform(get("/calendar/feed/busy.ics")).andExpect(status().isTooManyRequests());
    }

    @Test
    void feedUrl_withoutCookie_401() throws Exception {
        mvc.perform(get("/calendar/feed-url")).andExpect(status().isUnauthorized());
    }
}
