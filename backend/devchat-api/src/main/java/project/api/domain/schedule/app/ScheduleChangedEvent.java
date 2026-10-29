package project.api.domain.schedule.app;

// 시간·반복이 바뀌었거나 이번만 빼기를 했을 때만 낸다
public record ScheduleChangedEvent(Long scheduleId, Long actorId) {
}
