package project.api.domain.push.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.api.domain.chat.chatroom.app.ChatRoomService;
import project.api.domain.chat.chatroom.dto.AllRoomsResponse;

/**
 * 앱 아이콘 숫자 배지 = 안 읽은 채팅 메시지 수의 합 (카톡과 같은 기준).
 * 앱이 닫혀 있을 때는 푸시에 실어 보내 서비스 워커가 배지를 바꾼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeCountService {

    private final ChatRoomService chatRoomService;

    /** 계산에 실패하면 null (배지를 건드리지 않는다) */
    public Long totalUnread(Long memberId) {
        try {
            return chatRoomService.findAllRoomsByMemberId(memberId).stream()
                .map(AllRoomsResponse::getUnreadCount)
                .filter(c -> c != null && c > 0)
                .mapToLong(Long::longValue)
                .sum();
        } catch (Exception e) {
            log.warn("[배지] 안읽음 합계 계산 실패 member={}: {}", memberId, e.getMessage());
            return null;
        }
    }
}
