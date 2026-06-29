package project.backend.domain.chat.chatmessage.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.chat.chatroom.app.ChatRoomSequenceService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageSeqEventListener {

    private final ChatRoomSequenceService chatRoomSequenceService;

    @Async("chatSeqExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void genMessageSeq(ChatMessageSavedEvent event) {
        try {
            chatRoomSequenceService.genMessageSeq(event.roomId());
        } catch (Exception e) {
            log.error("[ChatSeq] Redis 및 DB fallback 모두 실패 - seq 유실, roomId={}, cause={}",
                    event.roomId(), e.getMessage());
        }
    }
}
