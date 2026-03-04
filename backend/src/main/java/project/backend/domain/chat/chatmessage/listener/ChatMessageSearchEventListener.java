package project.backend.domain.chat.chatmessage.listener;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.chat.chatmessage.dao.ChatMessageSearchRepository;
import project.backend.domain.chat.chatmessage.dto.event.ChatMessageSavedEvent;
import project.backend.domain.chat.chatmessage.entity.ChatMessageSearch;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageSearchEventListener {

    private final ChatMessageSearchRepository chatMessageSearchRepository;
    private final MeterRegistry meterRegistry;

    @Async("chatSearchEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSearchIndex(ChatMessageSavedEvent event) {
        try {
            chatMessageSearchRepository.save(
                ChatMessageSearch.builder()
                    .id(event.messageId())
                    .roomId(event.roomId())
                    .content(event.content())
                    .build()
            );
        } catch (Exception e) {
            log.error("[검색색인실패] messageId: {}", event.messageId());
            meterRegistry.counter("chat.search.index.fail").increment();
        }
    }
}
