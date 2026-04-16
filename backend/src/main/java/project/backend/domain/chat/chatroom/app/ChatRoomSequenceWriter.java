package project.backend.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;

@Component
@RequiredArgsConstructor
public class ChatRoomSequenceWriter {

    private final ChatRoomRepository chatRoomRepository;


}