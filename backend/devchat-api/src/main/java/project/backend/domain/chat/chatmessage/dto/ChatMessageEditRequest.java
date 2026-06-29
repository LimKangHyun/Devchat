package project.backend.domain.chat.chatmessage.dto;


import project.backend.domain.chat.chatmessage.entity.MessageType;

public record ChatMessageEditRequest(
    Long messageId,
    String content,
    MessageType type,
    String language //코드 메세지인 경우
) {

}
