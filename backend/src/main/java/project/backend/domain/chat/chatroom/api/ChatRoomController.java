package project.backend.domain.chat.chatroom.api;


import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.backend.domain.chat.chatroom.app.ChatRoomService;
import project.backend.domain.chat.chatroom.dto.ChatParticipantResponse;
import project.backend.domain.chat.chatroom.dto.ChatRoomNameResponse;
import project.backend.domain.chat.chatroom.dto.ChatRoomRequest;
import project.backend.domain.chat.chatroom.dto.ChatRoomSimpleResponse;
import project.backend.domain.chat.chatroom.dto.InviteJoinRequest;
import project.backend.domain.chat.chatroom.dto.InviteJoinResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import project.backend.domain.chat.chatroom.dto.MyChatRoomResponse;
import project.backend.domain.chat.chatroom.dto.ChatRoomDetailResponse;
import project.backend.domain.chat.chatroom.dto.ParticipantResponse;
import project.backend.domain.chat.chatroom.dto.RecentChatRoomResponse;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.global.config.security.dto.MemberDetails;
import project.backend.global.exception.errorcode.AuthErrorCode;
import project.backend.global.exception.ex.AuthException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat-rooms")
public class ChatRoomController {

	private final ChatRoomService chatRoomService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ChatRoomSimpleResponse createChatRoom(@Valid @RequestBody ChatRoomRequest request,
		@AuthenticationPrincipal MemberDetails memberDetails) {
		Long ownerId = memberDetails.getId();

		return chatRoomService.createChatRoom(request, ownerId);
	}

	@PostMapping("/join")
	public InviteJoinResponse joinChatRoom(@RequestBody InviteJoinRequest request,
		@AuthenticationPrincipal MemberDetails memberDetails
	) {
		if (memberDetails == null) {
			Long roomId = chatRoomService.getRoomIdByInviteCode(request.getInviteCode());
			throw new AuthException(AuthErrorCode.UNAUTHORIZED_USER, roomId,
				request.getInviteCode());
		}

		return chatRoomService.joinChatRoom(request.getInviteCode(), memberDetails.getId());
	}


	@GetMapping("/recent")
	public RecentChatRoomResponse getRecentRoomId(
		@AuthenticationPrincipal MemberDetails memberDetails) {
		Long roomId = chatRoomService.getMostRecentRoomId(memberDetails.getEmail());
		String inviteCode = chatRoomService.getInviteCode(roomId);
		return new RecentChatRoomResponse(roomId, inviteCode);
	}

	@GetMapping
	public Page<ChatRoomNameResponse> getChatRooms(
		@AuthenticationPrincipal MemberDetails memberDetails,
		@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		if (memberDetails == null) {
			throw new AuthException(AuthErrorCode.UNAUTHORIZED_USER);
		}
		Long memberId = memberDetails.getId();
		// 채팅방 목록 리스트로 가져오기
		return chatRoomService.findChatRoomsByMemberId(memberId, pageable);
	}

	@GetMapping("/{roomId}/participants")
	public List<ChatParticipantResponse> getParticipants(
		@PathVariable Long roomId,
		@AuthenticationPrincipal MemberDetails memberDetails) {

		if (memberDetails == null) {
			throw new AuthException(AuthErrorCode.UNAUTHORIZED_USER);
		}

		return chatRoomService.getParticipants(roomId);
	}

	// 자신이 만든 채팅방 가져오기 -> 주후 인증객체 id로 조회가능 할듯(Authentication)
	@GetMapping("/mine/{memberId}")
	public Page<MyChatRoomResponse> findMyAllChatRooms(@PathVariable Long memberId,
		@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		log.info("자신이 만든 채팅방 요청: memberId = {}", memberId);
		return chatRoomService.findAllRoomsByOwnerId(memberId, pageable);

	}

	//임창인 시작
	@DeleteMapping("/{roomId}/leave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leaveChatRoom(@PathVariable Long roomId,
		@AuthenticationPrincipal MemberDetails memberDetails) {
		chatRoomService.leaveChatRoom(roomId, memberDetails.getId());
	}
	//임창인 끝

	@GetMapping("/check")
	public ChatRoomNameResponse getChatRoomName(@RequestParam String inviteCode) {
		return chatRoomService.getChatRoomByInviteCode(inviteCode);
	}
}
