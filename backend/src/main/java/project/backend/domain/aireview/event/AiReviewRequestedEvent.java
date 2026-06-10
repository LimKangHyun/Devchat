package project.backend.domain.aireview.event;

import project.backend.domain.chat.chatroom.entity.ChatRoom;

public record AiReviewRequestedEvent(ChatRoom room, int prNumber, String headSha, String baseSha) {}
