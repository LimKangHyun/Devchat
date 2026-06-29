package project.backend.domain.chat.chatsearch;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchProjection;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatsearch.app.ChatMessageSearchService;
import project.backend.domain.chat.chatsearch.entity.ChatMessageSearch;
import project.backend.domain.chat.chatsearch.app.ChatMessageSearchScheduler;

@ExtendWith(MockitoExtension.class)
class ChatMessageSearchSchedulerTest {

    @InjectMocks
    private ChatMessageSearchScheduler scheduler;

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatMessageMapper messageMapper;
    @Mock private MeterRegistry meterRegistry;
    @Mock private ChatMessageSearchService searchService;

    @Nested
    @DisplayName("processSearchIndex() - 검색 색인 배치 처리")
    class ProcessSearchIndex {

        @Test
        @DisplayName("처리할 항목이 없으면 아무것도 실행하지 않는다")
        void processSearchIndex_empty_doNothing() {
            given(chatMessageRepository.findTop100WithIndexStatus()).willReturn(List.of());

            scheduler.processSearchIndex();

            then(searchService).should(never()).doProcess(anyList(), anyList());
        }

        @Test
        @DisplayName("정상 처리 시 doProcess를 호출한다")
        void processSearchIndex_success_callsDoProcess() {
            ChatMessageSearchProjection projection = mock(ChatMessageSearchProjection.class);
            given(projection.getId()).willReturn(1L);
            given(chatMessageRepository.findTop100WithIndexStatus()).willReturn(List.of(projection));

            ChatMessageSearch search = mock(ChatMessageSearch.class);
            given(messageMapper.toSearchEntity(projection)).willReturn(search);

            scheduler.processSearchIndex();

            then(searchService).should().doProcess(List.of(search), List.of(1L));
        }

        @Test
        @DisplayName("배치 실패 시 메트릭 카운터가 증가한다")
        void processSearchIndex_fail_incrementMetric() {
            ChatMessageSearchProjection projection = mock(ChatMessageSearchProjection.class);
            given(projection.getId()).willReturn(1L);
            given(chatMessageRepository.findTop100WithIndexStatus()).willReturn(List.of(projection));

            ChatMessageSearch search = mock(ChatMessageSearch.class);
            given(messageMapper.toSearchEntity(projection)).willReturn(search);

            willThrow(new RuntimeException("DB 오류")).given(searchService).doProcess(anyList(), anyList());

            Counter counter = mock(Counter.class);
            given(meterRegistry.counter("chat.search.index.fail")).willReturn(counter);

            scheduler.processSearchIndex();

            then(counter).should().increment();
        }
    }
}