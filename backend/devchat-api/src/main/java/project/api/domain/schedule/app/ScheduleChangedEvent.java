package project.api.domain.schedule.app;

import java.util.List;

// 시간·반복이 바뀌었거나 이번만 빼기를 했을 때만 낸다.
// memberIds: 알릴 참여자 (고치기면 원래 있었고 남은 사람, 이번만 빼기면 지금 참여자 전부)
public record ScheduleChangedEvent(Long scheduleId, Long actorId, List<Long> memberIds) {
}
