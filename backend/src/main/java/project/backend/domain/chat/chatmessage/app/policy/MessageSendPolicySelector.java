package project.backend.domain.chat.chatmessage.app.policy;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageSendPolicySelector {

    private final NormalMessageSendPolicy normal;
    private final DegradedMessageSendPolicy degraded;
    private final CircuitBreakerRegistry registry;

    public MessageSendPolicy select() {
        CircuitBreaker cb = registry.circuitBreaker("redis");

        if (cb.getState() == CircuitBreaker.State.OPEN) {
            return degraded;
        }

        return normal;
    }
}