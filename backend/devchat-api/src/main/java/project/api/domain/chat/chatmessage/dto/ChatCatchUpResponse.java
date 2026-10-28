package project.api.domain.chat.chatmessage.dto;

import java.util.List;

/**
 * 다시 연결됐을 때 놓친 메시지 (오래된 순서).
 * hasMore가 true면 마지막 메시지 id를 after로 넘겨 이어서 받는다.
 */
public record ChatCatchUpResponse(List<ChatMessageResponse> messages, boolean hasMore) {
}
