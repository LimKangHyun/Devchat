package project.backend.domain.chat.chatroom.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.app.ChatRoomService;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dto.event.DeleteChatRoomEvent;
import project.backend.domain.chat.chatmessage.dto.event.EventMessageResponse;
import project.backend.domain.chat.chatroom.dto.event.JoinChatRoomEvent;
import project.backend.domain.chat.chatroom.dto.event.LeaveChatRoomEvent;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.chat.chatroom.mapper.ChatRoomMapper;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.dto.event.ProfileUpdateEvent;
import project.backend.domain.member.entity.Member;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomEventListener {

	private final SimpMessagingTemplate simpMessagingTemplate;
	private final ChatMessageRepository chatMessageRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final ChatRoomService chatRoomService;
	private final ChatMessageMapper chatMessageMapper;
	private final MemberService memberService;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleMemberJoin(JoinChatRoomEvent joinEvent) {
		ChatRoom chatRoom = chatRoomService.getRoomById(joinEvent.roomId());
		Member member = memberService.getMemberById(joinEvent.memberId());

		ChatMessage message = chatMessageMapper.toEntityWithJoinEvent(chatRoom, member, joinEvent);
		ChatMessage savedMessage = chatMessageRepository.save(message);

		EventMessageResponse eventMessageResponse = ChatRoomMapper.toJoinEventMessageResponse(
			joinEvent, savedMessage.getId());

		// 입장 메시지 전송
		simpMessagingTemplate.convertAndSend("/topic/chat/" + joinEvent.roomId(),
			eventMessageResponse);
	}

	public void handleProfileUpdate(ProfileUpdateEvent updateEvent) {
		log.debug("🔥 프로필 업데이트 이벤트 수신: userId={}, nickname={}",
			updateEvent.userId(), updateEvent.nickname());

		simpMessagingTemplate.convertAndSend("/topic/profile-update", updateEvent);
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleMemberLeave(LeaveChatRoomEvent leaveEvent) {
		ChatRoom chatRoom = chatRoomService.getRoomById(leaveEvent.roomId());
		Member member = memberService.getMemberById(leaveEvent.memberId());

		ChatMessage message = chatMessageMapper.toEntityWithLeaveEvent(chatRoom, member, leaveEvent);
		ChatMessage savedMessage = chatMessageRepository.save(message);

		EventMessageResponse eventMessageResponse = ChatRoomMapper.toLeaveEventMessageResponse(
			leaveEvent, savedMessage.getId());

		// 퇴장 메시지 전송
		simpMessagingTemplate.convertAndSend("/topic/chat/" + leaveEvent.roomId(),
			eventMessageResponse);

		// 채팅방 인원 갱신 트리거 전송
		simpMessagingTemplate.convertAndSend("/topic/chat/" + leaveEvent.roomId() + "/refresh",
			leaveEvent.roomId());
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleRoomDelete(DeleteChatRoomEvent deleteEvent) {

		EventMessageResponse eventMessageResponse = ChatRoomMapper.toDeleteEventMessageResponse(
			deleteEvent
		);

		simpMessagingTemplate.convertAndSend("/topic/chat/" + deleteEvent.roomId() + "/deleted",
			eventMessageResponse);
	}
}