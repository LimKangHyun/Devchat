package project.api.global.websocket.interceptor.event;

/** clientMessageId: 막힌 SEND에 붙어 있던 임시 메시지 id (없으면 null) */
public record RateLimitExceededEvent(String username, String clientMessageId) {
}
