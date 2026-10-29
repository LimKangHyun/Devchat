package project.api.domain.schedule.app;

import java.util.List;

// 일정을 지웠거나 참여자를 뺐을 때. 지운 뒤에도 쓸 수 있게 제목을 같이 담는다
public record ScheduleCanceledEvent(Long scheduleId, Long actorId, String title,
                                    List<Long> memberIds) {
}
