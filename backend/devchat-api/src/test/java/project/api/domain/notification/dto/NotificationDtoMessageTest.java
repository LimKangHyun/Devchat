package project.api.domain.notification.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.entity.Notification;
import project.api.domain.notification.entity.NotificationType;

class NotificationDtoMessageTest {

    private Member member(Long id, String nickname) {
        return Member.builder().id(id).username("u" + id).nickname(nickname).profileImage("p").build();
    }

    @Test
    void usesMessageWhenPresent() {
        Notification n = Notification.ofSchedule(member(1L, "받는이"), member(2L, "민수"),
            NotificationType.SCHEDULE_ADDED, 7L, "민수님이 '회의' 일정에 추가했어요");

        NotificationDto dto = NotificationDto.ofNotification(n);

        assertThat(dto.content()).isEqualTo("민수님이 '회의' 일정에 추가했어요");
        assertThat(dto.referenceId()).isEqualTo(7L);
        assertThat(dto.type()).isEqualTo(NotificationType.SCHEDULE_ADDED);
    }

    @Test
    void fallsBackToTypeTextWhenNull() {
        Notification n = Notification.ofMention(member(1L, "받는이"), member(2L, "민수"), 5L);

        NotificationDto dto = NotificationDto.ofNotification(n);

        assertThat(dto.content()).isEqualTo("민수님이 채팅에서 회원님을 언급했습니다.");
    }
}
