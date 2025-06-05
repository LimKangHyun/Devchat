package project.backend.domain.chat.chatroom.app;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.dto.ChatParticipantResponse;
import project.backend.domain.chat.chatroom.dto.ChatRoomNameResponse;
import project.backend.domain.chat.chatroom.dto.ChatRoomRequest;
import project.backend.domain.chat.chatroom.dto.ChatRoomSimpleResponse;
import project.backend.domain.chat.chatroom.dto.InviteJoinResponse;
import project.backend.domain.chat.chatroom.dto.MyChatRoomResponse;
import project.backend.domain.chat.chatroom.dto.ParticipantResponse;
import project.backend.domain.chat.chatroom.dto.event.JoinChatRoomEvent;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.chat.github.app.GitMessageService;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatroom.dto.ChatRoomDetailResponse;
import project.backend.domain.chat.chatroom.mapper.ChatRoomMapper;
import project.backend.global.exception.errorcode.MemberErrorCode;
import project.backend.global.exception.ex.ChatRoomException;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.MemberException;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatRoomService {


	private final ChatRoomRepository chatRoomRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final ChatRoomMapper chatRoomMapper;
	private final MemberService memberService;
	private final GitMessageService gitMessageService;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${github.email-key}")
	private String githubEmailKey;

	@Transactional
	public ChatRoomSimpleResponse createChatRoom(ChatRoomRequest request, Long ownerId) {
		Member owner = memberService.getMemberById(ownerId);

		ChatRoom chatRoom = chatRoomMapper.toEntity(request);

		ChatParticipant chatParticipant = ChatParticipant.of(owner, chatRoom);
		chatParticipant.setOwner(true);
		chatRoom.addParticipant(chatParticipant);

		ChatRoom savedRoom = chatRoomRepository.save(chatRoom);

		if (!request.getRepositoryUrl().isBlank()) {
			gitMessageService.registerWebhook(request.getRepositoryUrl(),
				savedRoom.getId(), owner.getId()); //웹훅 자동 등록
			joinGitHubBot(savedRoom); //깃허브봇 채팅 참가
		}

		return chatRoomMapper.toSimpleResponse(savedRoom, owner);
	}

	private void joinGitHubBot(ChatRoom room) {
		Member githubBot = memberService.getMemberByEmail(githubEmailKey);
		ChatParticipant gitParticipant = ChatParticipant.of(githubBot, room);
		room.addParticipant(gitParticipant);
	}

	@Transactional(readOnly = true)
	public String getInviteCode(Long roomId) {
		ChatRoom room = getRoomById(roomId);

		return room.getInviteCode();
	}

	@Transactional
	public Long getRoomIdByInviteCode(String inviteCode) {
		ChatRoom room = getByInviteCode(inviteCode);

		return room.getId();
	}

	@Transactional
	public InviteJoinResponse joinChatRoom(String inviteCode, Long memberId) {
		ChatRoom room = getByInviteCode(inviteCode);

		Member member = memberService.getMemberById(memberId);

		validateNotParticipant(memberId,room.getId());

		ChatParticipant chatParticipant = ChatParticipant.of(member, room);

		room.addParticipant(chatParticipant);

		eventPublisher.publishEvent(
			new JoinChatRoomEvent(room.getId(), memberId, member.getNickname(),
				LocalDateTime.now()));

		return ChatRoomMapper.toInviteJoinResponse(room.getId(), room.getInviteCode(),
			room.getName());
	}

	@Transactional(readOnly = true)
	public String getRecentRoomInviteCode(Long memberId) {
		Long roomId = memberService.getMemberById(memberId).getRecentRoomId();

		// 아무 채팅방에도 참여한 적이 없음 → 예외 던지기
		if (roomId == null) {
			throw new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_EXIST);
		}

		return getRoomById(roomId).getInviteCode();
	}

	@Transactional(readOnly = true)
	public Page<MyChatRoomResponse> findAllRoomsByOwnerId(Long memberId, Pageable pageable) {
		Page<ChatRoom> allRoomsByOwnerId = chatRoomRepository.findAllRoomsByOwnerId(memberId,
			pageable);

		return allRoomsByOwnerId.map(ChatRoomMapper::toProfileResponse);
	}


	@Transactional(readOnly = true)
	public Page<ChatRoomNameResponse> findChatRoomsByMemberId(Long memberId, Pageable pageable) {

		Page<ChatRoom> chatRooms = chatRoomRepository.findChatRoomsByParticipantId(
			memberId, pageable);

		return chatRooms.map(ChatRoomMapper::toListResponse);
	}

	// 채팅방의 참가자 목록 조회
	@Transactional(readOnly = true)
	public List<ChatParticipantResponse> getParticipants(Long roomId) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(chatRoom);

		return participants.stream()
			.map(ChatRoomMapper::toParticipantResponse).collect(Collectors.toList());
	}

	//임창인
	@Transactional
	public void leaveChatRoom(Long roomId, Long memberId) {
		ChatRoom room = getRoomById(roomId);

		ChatParticipant participant = chatParticipantRepository.findByChatRoomIdAndParticipantId(
				roomId, memberId)
			.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT));

		if (participant.isOwner()) {
			throw new ChatRoomException(ChatRoomErrorCode.OWNER_CANNOT_LEAVE);
		}

		room.getParticipants().remove(participant);
	}

	private ChatRoom getByInviteCode(String inviteCode) {
		return chatRoomRepository.findByInviteCode(inviteCode)
			.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public ChatRoom getRoomById(Long roomId) {
		return chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public ChatRoomNameResponse getChatRoomDetails(String inviteCode,Long memberId) {
		ChatRoom room = getByInviteCode(inviteCode);

		validateNotParticipant(memberId,room.getId());

		return ChatRoomMapper.toListResponse(room);
	}

	private void validateNotParticipant(Long memberId, Long roomId) {
		if (!chatParticipantRepository.
			existsByParticipantIdAndChatRoomId(memberId, roomId)) {
			throw new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT);
		}
	}
}

