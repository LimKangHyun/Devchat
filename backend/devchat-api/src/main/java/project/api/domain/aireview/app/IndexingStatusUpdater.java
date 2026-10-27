package project.api.domain.aireview.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.common.enums.IndexingStatus;

@Component
@RequiredArgsConstructor
public class IndexingStatusUpdater {
    private final ChatRoomRepository chatRoomRepository;

    /**
     * AI 서버의 인덱싱 결과를 반영한다. 반영했으면 true.
     * 결과 메시지는 실패하면 나중에 재처리되므로, 늦게 도착한 결과가 최신 상태를 덮어쓰지 않게 조건을 둔다.
     * - RUNNING일 때만 반영한다. 예: 취소 후 다시 시작해 RUNNING인데 예전 "취소됨" 결과가 늦게 와도 무시된다.
     *   (RUNNING인 동안 인덱싱은 하나뿐이라, 그 사이에 오는 결과는 지금 실행의 결과다)
     * - 예외: COMPLETED는 FAILED도 덮어쓴다. AI 서버가 오래 내려가 있어 FAILED로 정리됐다가,
     *   나중에 요청을 처리해 실제로 인덱싱을 마친 경우다.
     */
    @Transactional
    public boolean applyResult(Long roomId, IndexingStatus result) {
        return chatRoomRepository.findById(roomId).map(room -> {
            IndexingStatus current = room.getIndexingStatus();
            boolean apply = current == IndexingStatus.RUNNING
                || (result == IndexingStatus.COMPLETED && current == IndexingStatus.FAILED);
            if (apply) room.updateIndexingStatus(result);
            return apply;
        }).orElse(false);
    }
}
