package project.api.domain.chat.chatroom.event;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import project.api.domain.chat.chatroom.app.ChatRoomSyncService;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRecoveryEventListener {

    private final ChatRoomSyncService chatRoomSyncService;
    private final CircuitBreakerRegistry registry;

    @PostConstruct
    public void init() {
        registry.circuitBreaker("redis")
                .getEventPublisher()
                .onStateTransition(event -> {
                    if (event.getStateTransition() ==
                            CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED) {
                        chatRoomSyncService.recoverSequences();
                    }
                });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        chatRoomSyncService.recoverSequences();
    }
}