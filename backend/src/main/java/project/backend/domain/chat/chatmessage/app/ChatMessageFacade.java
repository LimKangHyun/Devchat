package project.backend.domain.chat.chatmessage.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatroom.app.ChatRoomSequenceService;

@Service
@RequiredArgsConstructor
public class ChatMessageFacade {

    private final ChatRoomSequenceService chatRoomSequenceService;
    private final ChatMessageService chatMessageService;

    public ChatMessageResponse save(Long roomId, ChatMessageRequest request,
                                    MemberDetails memberDetails) {
        Long seq = chatRoomSequenceService.genMessageSeq(roomId, memberDetails.getId());
        return chatMessageService.saveWithSeq(roomId, request, memberDetails, seq);
    }
}