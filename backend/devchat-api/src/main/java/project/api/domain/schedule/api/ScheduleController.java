package project.api.domain.schedule.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.api.auth.dto.MemberDetails;
import project.api.domain.schedule.app.ScheduleService;
import project.api.domain.schedule.dto.OccurrenceResponse;
import project.api.domain.schedule.dto.ScheduleRequest;
import project.api.domain.schedule.dto.ScheduleResponse;

@Tag(name = "Schedule", description = "일정 API")
@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    public record ExclusionRequest(LocalDate date) {
    }

    @Operation(summary = "기간 안의 일정 회차 (최대 62일)")
    @GetMapping
    public List<OccurrenceResponse> occurrences(
        @AuthenticationPrincipal MemberDetails me,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return scheduleService.occurrences(me.getId(), from, to);
    }

    @Operation(summary = "다가오는 일정 회차 (최대 30개)")
    @GetMapping("/upcoming")
    public List<OccurrenceResponse> upcoming(@AuthenticationPrincipal MemberDetails me,
                                             @RequestParam(defaultValue = "10") int limit) {
        return scheduleService.upcoming(me.getId(), limit);
    }

    @Operation(summary = "일정 상세")
    @GetMapping("/{id}")
    public ScheduleResponse get(@AuthenticationPrincipal MemberDetails me, @PathVariable Long id) {
        return scheduleService.get(me.getId(), id);
    }

    @Operation(summary = "일정 만들기")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(@AuthenticationPrincipal MemberDetails me,
                                   @RequestBody ScheduleRequest request) {
        return scheduleService.create(me.getId(), request);
    }

    @Operation(summary = "일정 고치기 (반복 일정은 전체에 적용)")
    @PutMapping("/{id}")
    public ScheduleResponse update(@AuthenticationPrincipal MemberDetails me, @PathVariable Long id,
                                   @RequestBody ScheduleRequest request) {
        return scheduleService.update(me.getId(), id, request);
    }

    @Operation(summary = "일정 지우기 (반복 일정은 전체)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal MemberDetails me, @PathVariable Long id) {
        scheduleService.delete(me.getId(), id);
    }

    @Operation(summary = "반복 일정 이번만 빼기")
    @PostMapping("/{id}/exclusions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void exclude(@AuthenticationPrincipal MemberDetails me, @PathVariable Long id,
                        @RequestBody ExclusionRequest request) {
        scheduleService.excludeOccurrence(me.getId(), id, request.date());
    }

    @Operation(summary = "참여 중인 일정에서 나가기")
    @DeleteMapping("/{id}/participants/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal MemberDetails me, @PathVariable Long id) {
        scheduleService.leave(me.getId(), id);
    }
}
