package project.backend.domain.dm.dmMessage.dto;

import project.backend.domain.dm.dmMessage.DmMessageType;

public record DmMessageRequest(
    Long receiverId,
    String content,
    DmMessageType type
) {

}
