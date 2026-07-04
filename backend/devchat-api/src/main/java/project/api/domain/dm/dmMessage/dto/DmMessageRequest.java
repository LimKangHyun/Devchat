package project.api.domain.dm.dmMessage.dto;

import project.api.domain.dm.dmMessage.DmMessageType;

public record DmMessageRequest(
    String receiverUsername,
    String content,
    DmMessageType type
) {

}
