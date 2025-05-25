package project.backend.domain.chat.chatroom.mapper;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.chatroom.dto.ChatParticipantResponse;
import project.backend.domain.chat.chatroom.dto.ChatRoomNameResponse;
import project.backend.domain.chat.chatroom.dto.ChatRoomRequest;
import project.backend.domain.chat.chatroom.dto.ChatRoomSimpleResponse;
import project.backend.domain.chat.chatroom.dto.InviteJoinResponse;
import project.backend.domain.chat.chatroom.dto.MyChatRoomResponse;
import project.backend.domain.chat.chatroom.dto.event.EventMessageResponse;
import project.backend.domain.chat.chatroom.dto.event.JoinChatRoomEvent;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.member.entity.Member;

@Component
public class ChatRoomMapper {


	public static ChatRoomNameResponse toListResponse(ChatRoom chatRoom) {
		return ChatRoomNameResponse.builder()
			.roomId(chatRoom.getId())
			.roomName(chatRoom.getName())
			.repositoryUrl(chatRoom.getRepositoryUrl())
			.inviteCode(chatRoom.getInviteCode())
			.build();
	}

	// 강현님: 참여자 응답 변환
	public static ChatParticipantResponse toParticipantResponse(ChatParticipant p) {
		return ChatParticipantResponse.builder()
			.memberId(p.getParticipant().getId())
			.nickname(p.getParticipant().getNickname())
			.profileImageUrl(
				Optional.ofNullable(p.getParticipant().getProfileImage())
					.map(ImageFile::getStoreFileName)
					.orElse("default_image.jpg"))
			.isOwner(p.getParticipant().getId().equals(p.getChatRoom().getOwner().getId()))
			.build();
	}


	// 임창인: 간단 응답 변환
	public ChatRoomSimpleResponse toSimpleResponse(ChatRoom entity) {
		return ChatRoomSimpleResponse.of(
			entity.getId(),
			entity.getName(),
			entity.getRepositoryUrl(),
			entity.getOwner().getId(),
			entity.getInviteCode()
		);
	}


	// 임창인 엔티티 변환
	public ChatRoom toEntity(ChatRoomRequest dto, Member owner) {
		return ChatRoom.builder()
			.name(dto.getName())
			.createdAt(LocalDateTime.now())
			.repositoryUrl(dto.getRepositoryUrl())
			.inviteCode(generateInviteCode())
			.owner(owner)
			.build();
	}

	//문성이꺼
	public static MyChatRoomResponse toProfileResponse(ChatRoom chatRoom) {
		return MyChatRoomResponse.builder()
			.roomId(chatRoom.getId())
			.roomName(chatRoom.getName())
			.participantCount(chatRoom.getParticipants().size())
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

	public static EventMessageResponse toEventMessageResponse(JoinChatRoomEvent joinEvent) {
		return EventMessageResponse.builder()
			.type(MessageType.EVENT)
			.roomId(joinEvent.roomId())
			.sender(joinEvent.nickname())
			.content(joinEvent.nickname() + "님이 입장했습니다.")
			.joinedAt(joinEvent.joinedAt())
			.build();
	}
}
