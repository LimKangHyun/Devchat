package project.backend.domain.chat.chatroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.aireview.dto.AiSummaryToggleResponse;
import project.backend.domain.chat.chatroom.app.ChatRoomParticipantService;
import project.backend.domain.chat.chatroom.app.ChatRoomService;
import project.backend.domain.chat.chatroom.dto.AllRoomsResponse;
import project.backend.domain.chat.chatroom.dto.ChatParticipantResponse;
import project.backend.domain.chat.chatroom.dto.ChatRoomRequest;
import project.backend.domain.chat.chatroom.dto.ChatRoomSimpleResponse;
import project.backend.domain.chat.chatroom.dto.EntryRoomResponse;
import project.backend.domain.chat.chatroom.dto.InviteJoinRequest;
import project.backend.domain.chat.chatroom.dto.InviteJoinResponse;
import project.backend.domain.chat.chatroom.dto.MyChatRoomResponse;
import project.backend.domain.chat.chatroom.dto.RecentChatRoomResponse;
import project.backend.domain.chat.chatroom.dto.RoomInfoResponse;
import project.backend.domain.aireview.dto.AiReviewToggleResponse;

@Tag(name = "Chat Room", description = "채팅방 API")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat-rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatRoomParticipantService chatRoomParticipantService;

    @Operation(summary = "채팅방 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatRoomSimpleResponse createChatRoom(@Valid @RequestBody ChatRoomRequest request,
        @AuthenticationPrincipal MemberDetails memberDetails) {
        Long ownerId = memberDetails.getId();

        return chatRoomService.createChatRoom(request, ownerId);
    }

    @Operation(summary = "새로운 채팅방 참여")
    @PostMapping("/join")
    public InviteJoinResponse joinChatRoom(@RequestBody InviteJoinRequest request,
        @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        return chatRoomService.joinChatRoom(request.getInviteCode(), memberDetails.getId());
    }

    @Operation(summary = "최근 입장 채팅방 조회")
    @GetMapping("/recent")
    public RecentChatRoomResponse getRecentRoomInviteCode(
        @AuthenticationPrincipal MemberDetails memberDetails) {
        String inviteCode = chatRoomService.getRecentRoomInviteCode(
            memberDetails.getId());
        return new RecentChatRoomResponse(inviteCode);
    }

    @Operation(summary = "참여 중인 채팅방 목록 조회")
    @GetMapping
    public Page<RoomInfoResponse> getChatRooms(
        @AuthenticationPrincipal MemberDetails memberDetails,
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Long memberId = memberDetails.getId();
        return chatRoomService.findChatRoomsByMemberId(memberId, pageable);
    }

    @Operation(summary = "채팅방 참여자 조회")
    @GetMapping("/{roomId}/participants")
    public List<ChatParticipantResponse> getParticipants(
        @PathVariable Long roomId,
        @AuthenticationPrincipal MemberDetails memberDetails) {

        return chatRoomParticipantService.getParticipants(memberDetails.getId(), roomId);
    }

    @Operation(summary = "내가 생성한 채팅방 조회")
    @GetMapping("/mine")
    public Page<MyChatRoomResponse> findMyAllChatRooms(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return chatRoomService.findAllRoomsByOwnerId(memberDetails.getId(), pageable);
    }

    @Operation(summary = "채팅방 나가기")
    @DeleteMapping("/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveChatRoom(@PathVariable Long roomId,
        @AuthenticationPrincipal MemberDetails memberDetails) {
        chatRoomService.leaveChatRoom(roomId, memberDetails.getId());
    }

    @Operation(summary = "채팅방 입장")
    @GetMapping("/{inviteCode}")
    public EntryRoomResponse entryChatRoom(@PathVariable String inviteCode,
        @AuthenticationPrincipal MemberDetails memberDetails) {
        return chatRoomService.getEntryInfo(inviteCode, memberDetails.getId());
    }

    @Operation(summary = "채팅방 상세 정보 조회")
    @GetMapping("/info/{inviteCode}")
    public RoomInfoResponse getChatRoomDetails(@PathVariable String inviteCode,
        @AuthenticationPrincipal MemberDetails memberDetails) {
        return chatRoomService.getRoomInfo(inviteCode, memberDetails.getId());
    }

    @Operation(summary = "채팅방 삭제")
    @DeleteMapping("/{roomId}")
    public void deleteChatRoom(@PathVariable Long roomId,
        @AuthenticationPrincipal MemberDetails memberDetails) {
        chatRoomService.deleteChatRoom(roomId, memberDetails.getId());
    }

    @Operation(summary = "채팅방 알림 토글")
    @PostMapping("/alarm/toggle/{roomId}")
    public boolean toggleAlarm(@PathVariable Long roomId,
        @AuthenticationPrincipal MemberDetails memberDetails) {
        return chatRoomService.toggleAlarm(roomId, memberDetails.getId());
    }

    @Operation(summary = "참여중인 전체 채팅방 조회")
    @GetMapping("/all")
    public List<AllRoomsResponse> getAllRooms(
        @AuthenticationPrincipal MemberDetails memberDetails) {
        return chatRoomService.findAllRoomsByMemberId(memberDetails.getId());
    }

    @Operation(summary = "읽음 처리 (last read sequence 업데이트)")
    @PostMapping("/{roomId}/read")
    public void updateLastRead(
        @PathVariable Long roomId,
        @AuthenticationPrincipal MemberDetails memberDetails) {
        chatRoomService.updateLastReadSequence(roomId, memberDetails.getId());
    }

    @Operation(summary = "AI 리뷰 ON/OFF 토글")
    @PatchMapping("/{roomId}/ai-review/toggle")
    public ResponseEntity<AiReviewToggleResponse> toggleAiReview(
            @PathVariable Long roomId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        AiReviewToggleResponse response =
                chatRoomService.toggleAiReview(roomId, memberDetails.getId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "AI 요약 ON/OFF 토글")
    @PatchMapping("/{roomId}/ai-summary/toggle")
    public ResponseEntity<AiSummaryToggleResponse> toggleAiSummary(
            @PathVariable Long roomId,
            @AuthenticationPrincipal MemberDetails memberDetails) {
        return ResponseEntity.ok(chatRoomService.toggleAiSummary(roomId, memberDetails.getId()));
    }
}
