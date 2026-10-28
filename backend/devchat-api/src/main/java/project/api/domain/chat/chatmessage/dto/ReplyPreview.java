package project.api.domain.chat.chatmessage.dto;

import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatmessage.entity.MessageType;

/**
 * 답장 위에 작게 붙는 원문 미리보기.
 * 답장할 때 원문 모습을 저장해 두고 그대로 보여 준다 (원문을 나중에 고치거나 지워도 바뀌지 않는다).
 */
public record ReplyPreview(
        Long messageId,
        Long senderId,
        String senderName,
        MessageType type,
        String preview
) {
    static final int PREVIEW_LENGTH = 80;

    /** 저장된 답장 정보로 만든다 (답장이 아니면 null) */
    public static ReplyPreview of(ChatMessage reply) {
        if (reply == null || reply.getReplyToType() == null) return null;
        return new ReplyPreview(
                reply.getReplyToId(),
                reply.getReplyToSenderId(),
                reply.getReplyToSenderName(),
                reply.getReplyToType(),
                reply.getReplyToPreview()
        );
    }

    /** 원문을 한 줄로 줄인 미리보기 (답장할 때 저장한다) */
    public static String previewOf(ChatMessage original) {
        return switch (original.getType()) {
            case IMAGE -> "[이미지]";
            case CODE -> "[코드] " + firstLine(original.getContent());
            case AI_REVIEW -> "AI 코드 리뷰";
            default -> abbreviate(original.getContent());
        };
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        return abbreviate(s.strip().split("\\R", 2)[0]);
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        String oneLine = s.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= PREVIEW_LENGTH ? oneLine : oneLine.substring(0, PREVIEW_LENGTH) + "…";
    }
}
