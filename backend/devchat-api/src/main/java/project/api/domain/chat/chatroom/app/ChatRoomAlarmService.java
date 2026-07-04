package project.api.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.chat.chatroom.dao.ChatRoomAlarmRepository;
import project.api.domain.chat.chatroom.entity.ChatRoomAlarm;
import project.api.global.exception.errorcode.ChatRoomErrorCode;
import project.api.global.exception.ex.ChatRoomException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatRoomAlarmService {

    private final ChatRoomAlarmRepository chatRoomAlarmRepository;

    @Transactional
    public void createAlarm(Long memberId, Long roomId) {
        ChatRoomAlarm alarm = new ChatRoomAlarm(memberId, roomId);
        chatRoomAlarmRepository.save(alarm);
    }

    @Transactional
    public boolean toggleAlarm(Long roomId, Long memberId) {
        ChatRoomAlarm alarm = chatRoomAlarmRepository
                .findByIdMemberIdAndIdRoomId(memberId, roomId)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.ALARM_NOT_FOUND));

        log.debug("Before toggle: {}", alarm.isEnabled());
        alarm.setEnabled(!alarm.isEnabled());
        log.debug("After toggle: {}", alarm.isEnabled());

        return alarm.isEnabled();
    }

    public boolean isAlarmEnabled(Long memberId, Long roomId) {
        return chatRoomAlarmRepository.findEnabledByMemberIdAndRoomId(memberId, roomId);
    }

    public Map<Long, Boolean> findAlarmEnabledMap(Long memberId, List<Long> roomIds) {
        return chatRoomAlarmRepository.findEnabledMap(memberId, roomIds);
    }
}