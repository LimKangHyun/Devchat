package project.backend.domain.aireview.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.common.enums.IndexingStatus;

@Component
@RequiredArgsConstructor
public class IndexingStatusUpdater {
    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public void update(Long repoId, IndexingStatus status) {
        chatRoomRepository.findById(repoId).ifPresent(room -> {
            room.updateIndexingStatus(status);
            chatRoomRepository.save(room);
        });
    }
}