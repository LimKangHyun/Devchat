package project.api.domain.schedule.app;

import java.time.LocalDateTime;

// 반복 일정의 회차 하나. 하루 종일이면 end는 마지막 날 00:00 (끝 날짜 포함)
public record Occurrence(LocalDateTime start, LocalDateTime end) {
}
