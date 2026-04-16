package project.backend.domain.chat.chatmessage.app.policy;

public interface MessageSendPolicy {
    boolean canSend(Long userId);
}