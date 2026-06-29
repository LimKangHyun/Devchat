package project.backend.domain.chat.chatroom.mapper;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import project.backend.domain.chat.chatmessage.event.EventMessageResponse;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.chatroom.dto.ChatParticipantResponse;
import project.backend.domain.chat.chatroom.dto.ChatRoomRequest;
import project.backend.domain.chat.chatroom.dto.ChatRoomSimpleResponse;
import project.backend.domain.chat.chatroom.dto.InviteJoinResponse;
import project.backend.domain.chat.chatroom.dto.MyChatRoomResponse;
import project.backend.domain.chat.chatroom.dto.RoomInfoResponse;
import project.backend.domain.chat.chatroom.event.DeleteChatRoomEvent;
import project.backend.domain.chat.chatroom.event.JoinChatRoomEvent;
import project.backend.domain.chat.chatroom.event.LeaveChatRoomEvent;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.member.entity.Member;

@Component
public class ChatRoomMapper {

    public static RoomInfoResponse toListResponse(ChatRoom chatRoom) {
        return RoomInfoResponse.builder()
            .roomId(chatRoom.getId())
            .roomName(chatRoom.getName())
            .repositoryUrl(chatRoom.getRepositoryUrl())
            .inviteCode(chatRoom.getInviteCode())
            .build();
    }

    public static ChatParticipantResponse toParticipantResponse(ChatParticipant p) {
        return ChatParticipantResponse.builder()
            .memberId(p.getParticipant().getId())
            .nickname(p.getParticipant().getNickname())
            .profileImageUrl(p.getParticipant().getProfileImage())
            .isOwner(p.isOwner())
            .build();
    }

    public ChatRoomSimpleResponse toSimpleResponse(ChatRoom entity, Member owner) {
        return ChatRoomSimpleResponse.of(
            entity.getId(),
            entity.getName(),
            entity.getRepositoryUrl(),
            owner.getId(),
            entity.getInviteCode()
        );
    }

    public ChatRoom toEntity(ChatRoomRequest dto) {
        return ChatRoom.builder()
            .name(dto.getName())
            .createdAt(LocalDateTime.now())
            .repositoryUrl(dto.getRepositoryUrl())
            .inviteCode(generateInviteCode())
            .build();
    }

    public static MyChatRoomResponse toProfileResponse(ChatRoom chatRoom) {
        return MyChatRoomResponse.builder()
            .roomId(chatRoom.getId())
            .roomName(chatRoom.getName())
            .participantCount(chatRoom.getActiveParticipantCount())
            .inviteCode(chatRoom.getInviteCode())
            .build();
    }

    public static InviteJoinResponse toInviteJoinResponse(Long id, String inviteCode, String name) {
        return InviteJoinResponse.builder()
            .id(id)
            .inviteCode(inviteCode)
            .name(name)
            .build();
    }

    private static String generateInviteCode() {
        return UUID.randomUUID().toString();
    }

    public static EventMessageResponse toJoinEventMessageResponse(JoinChatRoomEvent joinEvent) {
        return EventMessageResponse.builder()
            .messageId(joinEvent.messageId())
            .type(MessageType.EVENT)
            .roomId(joinEvent.roomId())
            .sender(joinEvent.nickname())
            .content(joinEvent.nickname() + "님이 입장했습니다.")
            .createdAt(joinEvent.joinAt())
            .build();
    }

    public static EventMessageResponse toLeaveEventMessageResponse(LeaveChatRoomEvent leaveEvent,
        Long messageId) {
        return EventMessageResponse.builder()
            .messageId(messageId)
            .type(MessageType.EVENT)
            .roomId(leaveEvent.roomId())
            .sender(leaveEvent.nickname())
            .content(leaveEvent.nickname() + "님이 나갔습니다.")
            .createdAt(leaveEvent.leaveAt())
            .build();
    }

    public static EventMessageResponse toDeleteEventMessageResponse(
        DeleteChatRoomEvent deleteEvent) {
        return EventMessageResponse.builder()
            .type(MessageType.EVENT)
            .roomId(deleteEvent.roomId())
            .content("채팅방 '" + deleteEvent.roomName() + "'이 삭제되었습니다.")
            .build();
    }
}