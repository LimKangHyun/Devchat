package project.api.domain.chat.chatroom.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.api.domain.chat.chatroom.app.ChatRoomSequenceService;
import project.api.domain.chat.chatroom.app.ChatRoomService;
import project.api.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.api.domain.chat.chatmessage.event.EventMessageResponse;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.chat.chatroom.mapper.ChatRoomMapper;
import project.api.domain.github.event.GitSummaryRequestEvent;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.dto.event.ProfileUpdateEvent;
import project.api.domain.member.entity.Member;
import project.api.global.redis.RedisStreamClient;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomEventListener {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final ChatRoomSequenceService chatRoomSequenceService;
    private final ChatRoomService chatRoomService;
    private final MemberService memberService;

    private final ChatMessageMapper chatMessageMapper;
    private final RedisStreamClient redisStreamClient;

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleMemberJoin(JoinChatRoomEvent joinEvent) {
        chatRoomSequenceService.incrementCache(joinEvent.roomId());  // 실패해도 무해

        EventMessageResponse response = ChatRoomMapper.toJoinEventMessageResponse(joinEvent);
        simpMessagingTemplate.convertAndSend("/topic/chat/" + joinEvent.roomId(), response);
    }

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleProfileUpdate(ProfileUpdateEvent updateEvent) {
        log.debug("🔥 프로필 업데이트 이벤트 수신: userId={}, nickname={}",
            updateEvent.userId(), updateEvent.nickname());

        simpMessagingTemplate.convertAndSend("/topic/profile-update", updateEvent);
    }

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleMemberLeave(LeaveChatRoomEvent leaveEvent) {
        ChatRoom chatRoom = chatRoomService.getRoomById(leaveEvent.roomId());
        Member member = memberService.getMemberById(leaveEvent.memberId());

        chatRoomRedisRepository.genMessageSeq(leaveEvent.roomId());

        ChatMessage message = chatMessageMapper.toEntityWithLeaveEvent(chatRoom, member,
            leaveEvent);
        ChatMessage savedMessage = chatMessageRepository.save(message);

        chatRoomSequenceService.incrementCache(leaveEvent.roomId());

        EventMessageResponse eventMessageResponse = ChatRoomMapper.toLeaveEventMessageResponse(
            leaveEvent, savedMessage.getId());

        simpMessagingTemplate.convertAndSend("/topic/chat/" + leaveEvent.roomId(),
            eventMessageResponse);

        // 채팅방 인원 갱신 트리거 전송
        simpMessagingTemplate.convertAndSend("/topic/chat/" + leaveEvent.roomId() + "/refresh",
            leaveEvent.roomId());
    }

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleRoomDelete(DeleteChatRoomEvent deleteEvent) {

        EventMessageResponse eventMessageResponse = ChatRoomMapper.toDeleteEventMessageResponse(deleteEvent);
        simpMessagingTemplate.convertAndSend("/topic/chat/" + deleteEvent.roomId() + "/deleted",
            eventMessageResponse);
    }

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleChatRoomCreated(ChatRoomCreatedEvent event) {
        redisStreamClient.publishRepoIndexing(
                event.roomId(), event.repositoryUrl(), event.ownerId());
    }

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleGitSummaryRequest(GitSummaryRequestEvent event) {
        redisStreamClient.publishGitSummaryRequest(
                event.roomId(), event.messageId(), event.eventType(), event.prStatus(), event.fullContent());
    }
}