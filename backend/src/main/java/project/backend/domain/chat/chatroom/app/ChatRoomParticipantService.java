package project.backend.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dto.ChatParticipantResponse;
import project.backend.domain.chat.chatroom.dto.event.LeaveChatRoomEvent;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.chat.chatroom.mapper.ChatRoomMapper;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatRoomParticipantService {

    private final ChatParticipantRepository chatParticipantRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void handleParticipantJoin(ChatRoom room, Member member) {
        Optional<ChatParticipant> existingParticipant =
                chatParticipantRepository.findByChatRoomIdAndParticipantId(
                        room.getId(), member.getId());

        if (existingParticipant.isPresent()) {
            ChatParticipant participant = existingParticipant.get();
            if (participant.isActive()) {
                throw new ChatRoomException(ChatRoomErrorCode.ALREADY_PARTICIPANT);
            }
            participant.rejoin();
        } else {
            ChatParticipant chatParticipant = ChatParticipant.of(member, room);
            room.addParticipant(chatParticipant);
        }
    }

    @Transactional
    public void leaveChatRoom(Long roomId, Long memberId, String nickname) {
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoomIdAndParticipantIdAndIsActiveTrue(roomId, memberId)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT));

        if (participant.isOwner()) {
            throw new ChatRoomException(ChatRoomErrorCode.OWNER_CANNOT_LEAVE);
        }

        participant.leave();

        eventPublisher.publishEvent(
                new LeaveChatRoomEvent(roomId, memberId, nickname, LocalDateTime.now()));
    }

    public List<ChatParticipantResponse> getParticipants(Long memberId, Long roomId) {
        validateParticipant(memberId, roomId);
        ChatParticipant owner = chatParticipantRepository
                .findByChatRoomIdAndIsOwnerTrue(roomId)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.OWNER_NOT_FOUND));

        return chatParticipantRepository
                .findByChatRoom(owner.getChatRoom()).stream()
                .map(ChatRoomMapper::toParticipantResponse)
                .collect(Collectors.toList());
    }

    public Long findOwnerId(Long roomId) {
        return chatParticipantRepository.findByChatRoomIdAndIsOwnerTrue(roomId)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.OWNER_NOT_FOUND))
                .getParticipant().getId();
    }

    public void validateParticipant(Long memberId, Long roomId) {
        if (!chatParticipantRepository
                .existsByParticipantIdAndChatRoomIdAndIsActiveTrue(memberId, roomId)) {
            throw new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT);
        }
    }

    public Optional<ChatParticipant> findTopRecentActiveRoom(Long memberId) {
        return chatParticipantRepository
                .findTopByParticipantIdAndIsActiveTrueOrderByJoinAtDesc(memberId);
    }

    public Optional<ChatParticipant> findActiveParticipant(Long roomId, Long memberId) {
        return chatParticipantRepository
                .findByChatRoomIdAndParticipantIdAndIsActiveTrue(roomId, memberId);
    }

    @Transactional
    public void deleteAllByRoomId(Long roomId) {
        chatParticipantRepository.deleteByChatRoom_Id(roomId);
    }
}