package project.backend.domain.chat.chatmessage.dto;

import lombok.Getter;

@Getter
public class ChatMessageSearchRequest {

    private String keyword;
    private Long lastMessageId;
    private int pageSize;

    public static ChatMessageSearchRequest of(String keyword, Long lastMessageId, int pageSize) {
        ChatMessageSearchRequest request = new ChatMessageSearchRequest();
        request.keyword = keyword;
        request.lastMessageId = lastMessageId;
        request.pageSize = pageSize;
        return request;
    }

}

