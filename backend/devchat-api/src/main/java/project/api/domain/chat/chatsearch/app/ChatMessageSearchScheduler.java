package project.api.domain.chat.chatsearch.app;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatmessage.dto.ChatMessageSearchProjection;
import project.api.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.api.domain.chat.chatsearch.entity.ChatMessageSearch;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageSearchScheduler {

    private final ChatMessageSearchService searchService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMapper messageMapper;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 5000)
    public void processSearchIndex() {
        List<ChatMessageSearchProjection> searchData = chatMessageRepository
                .findTop100WithIndexStatus();
        if (searchData.isEmpty()) return;

        List<Long> messageIds = searchData.stream()
                .map(ChatMessageSearchProjection::getId)
                .toList();

        List<ChatMessageSearch> searchList = searchData.stream()
                .map(messageMapper::toSearchEntity)
                .toList();

        try {
            searchService.doProcess(searchList, messageIds);
            log.info("[검색색인] 배치 처리 완료: {}건", searchList.size());
        } catch (Exception e) {
            meterRegistry.counter("chat.search.index.fail").increment();
            log.error("[검색색인] 배치 처리 실패 - 재시도 예정: {}", e.getMessage());
        }
    }
}