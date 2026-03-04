package project.backend.domain.chat.chatmessage.app;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageSearchBatch {

    private final JdbcTemplate jdbcTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 300000)  // 5분
    public void reindexFailedMessages() {
        List<ChatMessage> missing = chatMessageRepository
            .findMissingSearchIndex(
                LocalDateTime.now().minusMinutes(5),
                PageRequest.of(0, 100)
            );

        if (missing.isEmpty()) {
            return;
        }

        for (ChatMessage message : missing) {
            try {
                jdbcTemplate.update(
                    "INSERT INTO chat_message_search (id, room_id, content) VALUES (?, ?, ?)",
                    message.getId(),
                    message.getChatRoomId(),
                    message.getContent()
                );
            } catch (Exception e) {
                log.error("[배치색인실패] messageId: {}", message.getId());
                meterRegistry.counter("chat.search.index.fail").increment();
            }
        }
    }
}