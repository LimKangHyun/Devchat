package project.api.domain.chat.chatmessage.dto;

import lombok.Data;
import project.api.domain.chat.chatmessage.entity.MessageType;

@Data
public class ChatMessageRequest {

    private String content;
    private MessageType type;
    private String language;
    private Long imageFileId;

}
