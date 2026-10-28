package project.api.domain.chat.chatmessage.event;

import project.api.domain.chat.chatmessage.dto.ChatMessageResponse;

/**
 * 같은 임시 id로 다시 보낸 메시지가 이미 저장돼 있을 때, 보낸 사람에게만 "이미 받았다"고 알린다.
 * 방 전체에 다시 보내면 다른 사람의 안읽음 수가 또 올라가므로 보낸 사람 세션에만 보낸다.
 */
public record ChatMessageAckEvent(String senderUsername, ChatMessageResponse message) {
}
