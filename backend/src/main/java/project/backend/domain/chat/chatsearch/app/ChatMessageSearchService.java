package project.backend.domain.chat.chatsearch.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatmessage.dao.ChatMessageIndexStatusRepository;
import project.backend.domain.chat.chatsearch.dao.ChatMessageSearchBulkRepository;
import project.backend.domain.chat.chatsearch.entity.ChatMessageSearch;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageSearchService {

    private final ChatMessageSearchBulkRepository bulkRepository;
    private final ChatMessageIndexStatusRepository indexStatusRepository;

    @Transactional
    public void doProcess(List<ChatMessageSearch> searchList, List<Long> messageIds) {
        bulkRepository.bulkInsertIgnore(searchList);
        indexStatusRepository.deleteAllByMessageIdIn(messageIds);
    }
}