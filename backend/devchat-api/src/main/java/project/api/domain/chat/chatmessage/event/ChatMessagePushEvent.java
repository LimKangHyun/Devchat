package project.api.domain.chat.chatmessage.event;

import java.util.List;

/**
 * 새 채팅 메시지를 휴대폰 알림으로 보낼 때 쓰는 이벤트.
 * mentionedMemberIds는 멘션 알림이 따로 가므로 채팅 알림에서 뺀다.
 */
public record ChatMessagePushEvent(
    Long roomId,
    Long senderId,
    String senderNickname,
    String preview,
    List<Long> mentionedMemberIds
) {
}
