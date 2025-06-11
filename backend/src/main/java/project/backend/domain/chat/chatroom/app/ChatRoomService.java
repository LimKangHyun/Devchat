package project.backend.domain.chat.chatroom.app;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.dto.ChatParticipantResponse;
import project.backend.domain.chat.chatroom.dto.ChatRoomRequest;
import project.backend.domain.chat.chatroom.dto.ChatRoomSimpleResponse;
import project.backend.domain.chat.chatroom.dto.EntryRoomResponse;
import project.backend.domain.chat.chatroom.dto.InviteJoinResponse;
import project.backend.domain.chat.chatroom.dto.MyChatRoomResponse;
import project.backend.domain.chat.chatroom.dto.RoomInfoResponse;
import project.backend.domain.chat.chatroom.dto.event.DeleteChatRoomEvent;
import project.backend.domain.chat.chatroom.dto.event.JoinChatRoomEvent;
import project.backend.domain.chat.chatroom.dto.event.LeaveChatRoomEvent;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.chat.chatroom.mapper.ChatRoomMapper;
import project.backend.domain.chat.github.app.GitMessageService;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatRoomService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final ChatRoomMapper chatRoomMapper;
	private final MemberService memberService;
	private final GitMessageService gitMessageService;
	private final ApplicationEventPublisher eventPublisher;
	private final ChatMessageRepository chatMessageRepository;

	@Value("${github.username}")
	private String githubUsername;

	@Transactional
	public ChatRoomSimpleResponse createChatRoom(ChatRoomRequest request, Long ownerId) {
		Member owner = memberService.getMemberById(ownerId);

		ChatRoom chatRoom = chatRoomMapper.toEntity(request);

		ChatParticipant chatParticipant = ChatParticipant.createOwner(owner, chatRoom);
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
		Member githubBot = memberService.getMemberByUsername(githubUsername);
		ChatParticipant gitParticipant = ChatParticipant.of(githubBot, room);
		room.addParticipant(gitParticipant);
	}

	@Transactional(readOnly = true)
	public String getInviteCode(Long roomId) {
		ChatRoom room = getRoomById(roomId);

		return room.getInviteCode();
	}

	@Transactional(readOnly = true)
	public Long getRoomIdByInviteCode(String inviteCode) {
		ChatRoom room = getByInviteCode(inviteCode);

		return room.getId();
	}

	@Transactional
	public InviteJoinResponse joinChatRoom(String inviteCode, Long memberId) {
		ChatRoom room = getByInviteCode(inviteCode);
		Member member = memberService.getMemberById(memberId);

		handleParticipantJoin(room, member);

		eventPublisher.publishEvent(
			new JoinChatRoomEvent(room.getId(), memberId, member.getNickname(),
				LocalDateTime.now()));

		return ChatRoomMapper.toInviteJoinResponse(room.getId(), room.getInviteCode(),
			room.getName());
	}

	private void handleParticipantJoin(ChatRoom room, Member member) {
		//참여중 여부와 관계없이 기존 참가 기록들을 확인
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
	public Page<RoomInfoResponse> findChatRoomsByMemberId(Long memberId, Pageable pageable) {

		Page<ChatRoom> chatRooms = chatRoomRepository.findChatRoomsByParticipantId(
			memberId, pageable);

		return chatRooms.map(ChatRoomMapper::toListResponse);
	}

	// 채팅방의 참가자 목록 조회
	@Transactional(readOnly = true)
	public List<ChatParticipantResponse> getParticipants(Long memberId, Long roomId) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		validateNotParticipant(memberId, roomId);

		List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(chatRoom);

		return participants.stream()
			.map(ChatRoomMapper::toParticipantResponse).collect(Collectors.toList());
	}

	//임창인
	@Transactional
	public void leaveChatRoom(Long roomId, Long memberId) {

		ChatParticipant participant = chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(
				roomId, memberId)
			.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT));

		if (participant.isOwner()) {
			throw new ChatRoomException(ChatRoomErrorCode.OWNER_CANNOT_LEAVE);
		}

		participant.leave();

		Member member = memberService.getMemberById(memberId);

		eventPublisher.publishEvent(
			new LeaveChatRoomEvent(roomId, memberId, member.getNickname(),
				LocalDateTime.now()));

		updateRecentRoomAfterLeaving(memberId);
	}

	private void updateRecentRoomAfterLeaving(Long memberId) {
		Member member = memberService.getMemberById(memberId);

		Optional<ChatParticipant> mostRecentActiveRoom =
			chatParticipantRepository.findTopByParticipantIdAndIsActiveTrueOrderByJoinAtDesc(memberId);

		if (mostRecentActiveRoom.isPresent()) {
			member.setRecentRoomId(mostRecentActiveRoom.get().getChatRoom().getId());
		} else {
			member.setRecentRoomId(null);
		}

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

	@Transactional
	public EntryRoomResponse getEntryInfo(String inviteCode, Long memberId) {
		ChatRoom room = getByInviteCode(inviteCode);
		validateNotParticipant(memberId, room.getId());

		memberService.getMemberById(memberId).setRecentRoomId(room.getId()); //recentRoomId 업데이트

		Long ownerId = findOwnerId(room.getId());

		return new EntryRoomResponse(room.getId(), room.getName(), ownerId);
	}

	private Long findOwnerId(Long roomId) {
		ChatParticipant owner = chatParticipantRepository.findByChatRoomIdAndIsOwnerTrue(roomId)
			.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.OWNER_NOT_FOUND));
		return owner.getParticipant().getId();
	}

	@Transactional(readOnly = true)
	public RoomInfoResponse getRoomInfo(String inviteCode, Long memberId) {
		ChatRoom room = getByInviteCode(inviteCode);
		return ChatRoomMapper.toListResponse(room);
	}

	public void validateNotParticipant(Long memberId, Long roomId) {
		if (!chatParticipantRepository.
			existsByParticipantIdAndChatRoomIdAndIsActiveTrue(memberId, roomId)) {
			throw new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT);
		}
	}

	@Transactional
	public void deleteChatRoom(Long roomId, Long memberId) {
		ChatRoom room = getRoomById(roomId);

		ChatParticipant participant = chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(
				roomId, memberId)
			.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT));

		if (!participant.isOwner()) {
			throw new ChatRoomException(ChatRoomErrorCode.OWNER_PERMISSION_REQUIRED);
		}

		eventPublisher.publishEvent(
			new DeleteChatRoomEvent(roomId, room.getName())
		);

		chatRoomRepository.delete(room);
	}
}

