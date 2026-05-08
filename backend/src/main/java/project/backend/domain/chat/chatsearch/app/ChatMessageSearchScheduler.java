package project.backend.domain.chat.chatsearch.app;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatmessage.dao.ChatMessageIndexStatusRepository;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessageIndexStatus;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatsearch.dao.ChatMessageSearchBulkRepository;
import project.backend.domain.chat.chatsearch.entity.ChatMessageSearch;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageSearchScheduler {

    private final ChatMessageIndexStatusRepository indexStatusRepository;
    private final ChatMessageSearchBulkRepository bulkRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMapper messageMapper;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processSearchIndex() {
        List<ChatMessageIndexStatus> targets = indexStatusRepository
                .findTop100ByOrderByMessageIdAsc();
        if (targets.isEmpty()) return;

        List<Long> messageIds = targets.stream()
                .map(ChatMessageIndexStatus::getMessageId)
                .toList();

        List<ChatMessageSearch> searchList = chatMessageRepository.findSearchDataByIdIn(messageIds)
                .stream()
                .map(messageMapper::toSearchEntity)
                .toList();

        try {
            bulkRepository.bulkInsertIgnore(searchList);
            indexStatusRepository.deleteAllByMessageIdIn(messageIds);
            log.info("[검색색인] 배치 처리 완료: {}건", searchList.size());
        } catch (Exception e) {
            meterRegistry.counter("chat.search.index.fail").increment();
            log.error("[검색색인] 배치 처리 실패 - 재시도 예정: {}", e.getMessage());
        }
    }
}