package project.backend.global.websocket.interceptor.event;

public record RateLimitExceededEvent(String username) {
}
