package project.api.domain.chat.chatmessage.dto;

import java.util.List;
import lombok.Data;
import project.api.domain.chat.chatmessage.entity.MessageType;

@Data
public class ChatMessageRequest {

    private String content;
    private MessageType type;
    private String language;
    private Long imageFileId;
    /** @멘션한 참여자 id. 서버가 실제 방 참여자인지 다시 확인한다 */
    private List<Long> mentionedMemberIds;

}
