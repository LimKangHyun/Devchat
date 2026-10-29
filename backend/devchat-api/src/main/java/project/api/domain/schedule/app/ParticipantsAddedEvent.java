package project.api.domain.schedule.app;

import java.util.List;

// 참여자가 새로 추가됐을 때. memberIds는 새로 들어온 사람만
public record ParticipantsAddedEvent(Long scheduleId, Long actorId, List<Long> memberIds) {
}
