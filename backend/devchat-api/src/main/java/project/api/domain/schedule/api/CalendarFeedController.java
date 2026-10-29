package project.api.domain.schedule.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.api.domain.member.app.MemberService;
import project.api.domain.schedule.app.CalendarFeedService;
import project.api.domain.schedule.dto.FeedUrlResponse;

@Tag(name = "Calendar Feed", description = "애플 캘린더 구독 주소 API")
@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarFeedController {

    private static final MediaType TEXT_CALENDAR =
        new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final CalendarFeedService feedService;
    private final MemberService memberService;

    @Operation(summary = "구독 주소 보기", description = "없으면 새로 만든다.")
    @GetMapping("/feed-url")
    public FeedUrlResponse feedUrl(Authentication auth) {
        return feedService.getOrCreateUrl(memberService.checkAuthentication(auth).getId());
    }

    @Operation(summary = "구독 주소 새로 만들기", description = "예전 주소는 바로 못 쓴다.")
    @PostMapping("/feed-url/regenerate")
    public FeedUrlResponse regenerate(Authentication auth) {
        return feedService.regenerate(memberService.checkAuthentication(auth).getId());
    }

    // 로그인 없이 열린다 (애플 캘린더가 직접 가져간다)
    @Operation(summary = "캘린더 피드(.ics)", description = "토큰이 틀리면 404, 시간당 120회를 넘으면 429.")
    @GetMapping("/feed/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        return ResponseEntity.ok()
            .contentType(TEXT_CALENDAR)
            .cacheControl(CacheControl.noCache())
            .body(feedService.feed(token));
    }
}
