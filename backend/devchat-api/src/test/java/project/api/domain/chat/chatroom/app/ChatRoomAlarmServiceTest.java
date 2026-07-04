package project.api.domain.chat.chatroom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import project.api.domain.chat.chatroom.dao.ChatRoomAlarmRepository;
import project.api.domain.chat.chatroom.entity.ChatRoomAlarm;
import project.api.global.exception.ex.ChatRoomException;

@ExtendWith(MockitoExtension.class)
class ChatRoomAlarmServiceTest {

    @InjectMocks
    private ChatRoomAlarmService chatRoomAlarmService;

    @Mock private ChatRoomAlarmRepository chatRoomAlarmRepository;

    @Nested
    @DisplayName("toggleAlarm() - 알람 토글")
    class ToggleAlarm {

        @Test
        @DisplayName("알람이 켜져 있으면 끄고 false를 반환한다")
        void toggle_enabled_returnsDisabled() {
            ChatRoomAlarm alarm = mock(ChatRoomAlarm.class);
            given(alarm.isEnabled()).willReturn(false);
            given(chatRoomAlarmRepository.findByIdMemberIdAndIdRoomId(1L, 10L))
                    .willReturn(Optional.of(alarm));

            boolean result = chatRoomAlarmService.toggleAlarm(10L, 1L);

            assertThat(result).isFalse();
            then(alarm).should().setEnabled(true);
        }

        @Test
        @DisplayName("알람이 꺼져 있으면 켜고 true를 반환한다")
        void toggle_disabled_returnsEnabled() {
            ChatRoomAlarm alarm = mock(ChatRoomAlarm.class);
            given(alarm.isEnabled()).willReturn(true);
            given(chatRoomAlarmRepository.findByIdMemberIdAndIdRoomId(1L, 10L))
                    .willReturn(Optional.of(alarm));

            boolean result = chatRoomAlarmService.toggleAlarm(10L, 1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("알람 정보가 없으면 예외를 던진다")
        void toggle_notFound_throwsException() {
            given(chatRoomAlarmRepository.findByIdMemberIdAndIdRoomId(1L, 10L))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> chatRoomAlarmService.toggleAlarm(10L, 1L))
                    .isInstanceOf(ChatRoomException.class);
        }
    }
}