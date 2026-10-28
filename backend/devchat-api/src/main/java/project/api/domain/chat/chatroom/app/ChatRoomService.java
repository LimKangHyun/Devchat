package project.api.domain.chat.chatroom.app;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.aireview.dto.AiSummaryToggleResponse;
import project.api.domain.chat.chatmessage.app.ChatMessageService;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatroom.dao.ChatRoomDataCleaner;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomWithSequenceProjection;
import project.api.domain.chat.chatroom.dto.*;
import project.api.domain.chat.chatroom.entity.*;
import project.api.domain.chat.chatroom.event.ChatRoomCreatedEvent;
import project.common.enums.IndexingStatus;
import project.api.domain.chat.chatroom.event.DeleteChatRoomEvent;
import project.api.domain.chat.chatroom.event.JoinChatRoomEvent;
import project.api.domain.chat.chatroom.event.RoomCreatedByMemberEvent;
import project.api.domain.chat.chatroom.mapper.ChatRoomMapper;
import project.api.domain.aireview.app.AiReviewService;
import project.api.domain.github.app.GitMessageService;
import project.api.domain.community.dao.PostRepository;
import project.api.domain.aireview.dto.AiReviewToggleResponse;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.entity.Member;
import project.api.global.exception.errorcode.ChatRoomErrorCode;
import project.api.global.exception.ex.ChatRoomException;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatRoomService {

    private final PostRepository postRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomDataCleaner chatRoomDataCleaner;

    private final ChatRoomMapper chatRoomMapper;

    private final MemberService memberService;
    private final ChatMessageService chatMessageService;
    private final GitMessageService gitMessageService;
    private final ChatRoomAlarmService chatRoomAlarmService;
    private final ChatRoomReadService chatRoomReadService;
    private final ChatRoomParticipantService chatRoomParticipantService;
    private final ChatRoomSyncService chatRoomSyncService;
    private final AiReviewService aiReviewService;

    private final ApplicationEventPublisher eventPublisher;

    @Value("${github.username}")
    private String githubUsername;

    @Value("${github.bot.username}")
    private String aiReviewerUsername;

    @Transactional
    public ChatRoomSimpleResponse createChatRoom(ChatRoomRequest request, Long ownerId) {
        Member owner = memberService.getMemberById(ownerId);
        // 레포 없이 만드는 방은 repositoryUrl이 빠져(null) 올 수 있다
        boolean hasRepository = request.getRepositoryUrl() != null && !request.getRepositoryUrl().isBlank();

        if (hasRepository &&
            chatRoomRepository.existsByRepositoryUrl(request.getRepositoryUrl())) {
            throw new ChatRoomException(ChatRoomErrorCode.REPOSITORY_ALREADY_CONNECTED);
        }

        ChatRoom chatRoom = chatRoomMapper.toEntity(request);
        ChatParticipant chatParticipant = ChatParticipant.createOwner(owner, chatRoom);
        chatRoom.addParticipant(chatParticipant);
        ChatRoom savedRoom = chatRoomRepository.save(chatRoom);

        chatRoomAlarmService.createAlarm(ownerId, chatRoom.getId());
        // 만든 사람의 다른 창·기기에도 새 방이 사이드바에 보이도록
        eventPublisher.publishEvent(new RoomCreatedByMemberEvent(savedRoom.getId(), ownerId));

        if (hasRepository) {
            gitMessageService.registerWebhook(request.getRepositoryUrl(),
                    savedRoom.getId(), owner.getId()); // 트랜잭션내에서 수행
            joinGitHubBot(savedRoom); // 트랜잭션 내에서 수행
            joinReviewBot(savedRoom); // 트랜잭션 내에서 수행
            savedRoom.updateIndexingStatus(IndexingStatus.RUNNING);
            eventPublisher.publishEvent(
                    new ChatRoomCreatedEvent(savedRoom.getId(), request.getRepositoryUrl(), ownerId));
        }
        return chatRoomMapper.toSimpleResponse(savedRoom, owner);
    }

    private void joinGitHubBot(ChatRoom room) {
        Member githubBot = memberService.getMemberByUsername(githubUsername);
        ChatParticipant gitParticipant = ChatParticipant.of(githubBot, room);
        room.addParticipant(gitParticipant);
    }

    private void joinReviewBot(ChatRoom room) {
        Member reviewBot = memberService.getMemberByUsername(aiReviewerUsername);
        ChatParticipant gitParticipant = ChatParticipant.of(reviewBot, room);
        room.addParticipant(gitParticipant);
    }

    public InviteJoinResponse joinChatRoom(String inviteCode, Long memberId) {
        try {
            ChatRoom room = getByInviteCodeWithLock(inviteCode);
            Member member = memberService.getMemberById(memberId);

            chatRoomParticipantService.handleParticipantJoin(room, member);
            chatRoomAlarmService.createAlarm(memberId, room.getId());

            ChatMessage savedMessage = chatMessageService.saveJoinEvent(room, member);

            eventPublisher.publishEvent(
                    new JoinChatRoomEvent(
                            room.getId(),
                            memberId,
                            member.getNickname(),
                            savedMessage.getId(),
                            savedMessage.getCreatedAt()
                    )
            );
            return ChatRoomMapper.toInviteJoinResponse(
                    room.getId(),
                    room.getInviteCode(),
                    room.getName()
            );
        } catch (PessimisticLockException | LockTimeoutException e) {
            throw new ChatRoomException(ChatRoomErrorCode.TRY_AGAIN);
        }
    }

    private ChatRoom getByInviteCodeWithLock(String inviteCode) {
        return chatRoomRepository.findByInviteCodeWithLock(inviteCode)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public String getRecentRoomInviteCode(Long memberId) {
        Long roomId = memberService.getMemberById(memberId).getRecentRoomId();
        if (roomId == null) {
            throw new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_EXIST);
        }
        return getRoomById(roomId).getInviteCode();
    }

    public Page<MyChatRoomResponse> findAllRoomsByOwnerId(Long memberId, Pageable pageable) {
        return chatRoomRepository.findAllRoomsByOwnerId(memberId, pageable)
                .map(ChatRoomMapper::toProfileResponse);
    }

    public void leaveChatRoom(Long roomId, Long memberId) {
        Member member = memberService.getMemberById(memberId);
        chatRoomParticipantService.leaveChatRoom(roomId, memberId, member.getNickname());
        updateRecentRoomAfterLeaving(memberId);
    }

    private void updateRecentRoomAfterLeaving(Long memberId) {
        Member member = memberService.getMemberById(memberId);
        chatRoomParticipantService.findTopRecentActiveRoom(memberId)
                .ifPresentOrElse(
                        p -> member.updateRecentRoom(p.getChatRoom().getId()),
                        member::clearRecentRoom
                );
    }

    @Transactional(readOnly = true)
    public List<AllRoomsResponse> findAllRoomsByMemberId(Long memberId) {
        List<ChatRoomWithSequenceProjection> roomProjections =
                chatRoomRepository.findAllRoomsWithSequenceByParticipantId(memberId);

        List<Long> roomIds = roomProjections.stream()
                .map(ChatRoomWithSequenceProjection::getChatRoomId)
                .collect(Collectors.toList());

        Map<Long, Boolean> alarmEnabledMap = roomIds.isEmpty()
                ? Collections.emptyMap()
                : chatRoomAlarmService.findAlarmEnabledMap(memberId, roomIds);

        return chatRoomReadService.findAllRoomsWithUnread(roomProjections, alarmEnabledMap);
    }

    // 입장 시 최근 방·읽음 위치를 저장하므로 쓰기 트랜잭션이어야 한다
    @Transactional
    public EntryRoomResponse getEntryInfo(String inviteCode, Long memberId) {
        ChatRoom room = getByInviteCode(inviteCode);

        ChatParticipant participant = chatRoomParticipantService
                .findActiveParticipant(room.getId(), memberId)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT));

        Long currentSequence = chatRoomReadService.getLatestSequence(room.getId());
        participant.updateLastReadSequence(currentSequence);

        memberService.getMemberById(memberId).updateRecentRoom(room.getId());

        Long ownerId = chatRoomParticipantService.findOwnerId(room.getId());
        boolean alarmEnable = chatRoomAlarmService.isAlarmEnabled(memberId, room.getId());

        return new EntryRoomResponse(
                room.getId(), room.getName(), ownerId, alarmEnable, room.getRepositoryUrl(),
                room.isAiReviewEnabled(), room. isAiSummaryEnabled(), room.getIndexingStatus()
        );
    }

    @Transactional(readOnly = true)
    public RoomInfoResponse getRoomInfo(String inviteCode, Long memberId) {
        ChatRoom room = getByInviteCode(inviteCode);
        return ChatRoomMapper.toListResponse(room);
    }

    /**
     * DeleteChatRoomEvent의 리스너(웹훅·벡터 인덱스 정리, 삭제 알림)는 AFTER_COMMIT에 실행된다.
     * 트랜잭션 없이 이벤트를 발행하면 커밋 시점이 없어 리스너가 호출되지 않으므로 반드시 트랜잭션 안에서 실행한다.
     */
    @Transactional
    public void deleteChatRoom(Long roomId, Long memberId) {
        ChatRoom room = getRoomById(roomId);

        ChatParticipant participant = chatRoomParticipantService
                .findActiveParticipant(roomId, memberId)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT));

        if (!participant.isOwner()) {
            throw new ChatRoomException(ChatRoomErrorCode.OWNER_PERMISSION_REQUIRED);
        }

        eventPublisher.publishEvent(
                new DeleteChatRoomEvent(roomId, room.getName(), room.getRepositoryUrl(), room.getWebhookId(), memberId)
        );

        // 게시글은 지원자(applicant)까지 엔티티 cascade로 지워야 해서 JPA로 지운다. 나머지는 테이블별 일괄 삭제
        postRepository.deleteByChatRoom_Id(roomId);
        chatRoomDataCleaner.deleteRoomAndData(roomId);
    }

    public boolean toggleAlarm(Long roomId, Long memberId) {
        return chatRoomAlarmService.toggleAlarm(roomId, memberId);
    }

    public void updateLastReadSequence(Long roomId, Long memberId) {
        chatRoomReadService.updateLastReadSequence(roomId, memberId);
    }

    @Transactional(readOnly = true)
    public ChatRoom getRoomById(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    }

    private ChatRoom getByInviteCode(String inviteCode) {
        return chatRoomRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    }

    public AiReviewToggleResponse toggleAiReview(Long roomId, Long memberId) {
        ChatRoom room = findRoomAsOwner(roomId, memberId);
        room.toggleAiReview();
        return new AiReviewToggleResponse(room.isAiReviewEnabled());
    }

    public AiSummaryToggleResponse toggleAiSummary(Long roomId, Long memberId) {
        ChatRoom room = findRoomAsOwner(roomId, memberId);
        room.toggleAiSummary();
        return new AiSummaryToggleResponse(room.isAiSummaryEnabled());
    }

    /** 팀 리뷰 규칙은 참여자 누구나 볼 수 있다. 리뷰가 어떤 기준으로 달리는지 팀원도 알아야 하기 때문이다. */
    @Transactional(readOnly = true)
    public ReviewConventionsResponse getReviewConventions(Long roomId, Long memberId) {
        chatRoomParticipantService.validateParticipant(memberId, roomId);
        return new ReviewConventionsResponse(getRoomById(roomId).getReviewConventions());
    }

    /**
     * 팀 리뷰 규칙은 방장만 수정한다.
     * 규칙은 AI 리뷰 프롬프트에 그대로 들어가서, 누구나 고칠 수 있으면 리뷰 기준을 마음대로 바꿀 수 있다.
     */
    public ReviewConventionsResponse updateReviewConventions(Long roomId, Long memberId, String conventions) {
        ChatRoom room = findRoomAsOwner(roomId, memberId);
        room.updateReviewConventions(conventions);
        return new ReviewConventionsResponse(room.getReviewConventions());
    }

    /**
     * 연결된 저장소의 GitHub webhook을 지금 설정(서명 secret, push 포함 이벤트)으로 다시 맞춘다. 방장만 할 수 있다.
     * secret 없이 등록된 예전 webhook은 이 작업 전까지 서명 검증을 통과하지 못한다(설정에 따라 경고만 남기거나 거절).
     */
    public void refreshWebhook(Long roomId, Long memberId) {
        ChatRoom room = findRoomAsOwner(roomId, memberId);
        if (room.getRepositoryUrl() == null || room.getRepositoryUrl().isBlank()) {
            throw new ChatRoomException(ChatRoomErrorCode.REPOSITORY_NOT_CONNECTED);
        }
        gitMessageService.refreshWebhook(roomId, memberId);
    }

    // 레포 없이 만든 방에 나중에 레포를 연결한다. 방 생성 때와 같은 순서: 웹훅 → 봇 참여 → 인덱싱
    public ConnectRepositoryResponse connectRepository(Long roomId, Long memberId, String repositoryUrl) {
        ChatRoom room = findRoomAsOwner(roomId, memberId);
        String url = repositoryUrl.trim();

        if (room.getRepositoryUrl() != null && !room.getRepositoryUrl().isBlank()) {
            throw new ChatRoomException(ChatRoomErrorCode.ROOM_REPOSITORY_ALREADY_SET);
        }
        if (chatRoomRepository.existsByRepositoryUrl(url)) {
            throw new ChatRoomException(ChatRoomErrorCode.REPOSITORY_ALREADY_CONNECTED);
        }

        // URL 검증 + 관리자 권한 확인도 여기서 한다 (실패하면 아무것도 바뀌지 않음)
        gitMessageService.registerWebhook(url, room.getId(), memberId);
        room.connectRepository(url);

        joinBotIfAbsent(room, githubUsername);
        joinBotIfAbsent(room, aiReviewerUsername);

        room.updateIndexingStatus(IndexingStatus.RUNNING);
        eventPublisher.publishEvent(new ChatRoomCreatedEvent(room.getId(), url, memberId));
        return new ConnectRepositoryResponse(url);
    }

    private void joinBotIfAbsent(ChatRoom room, String botUsername) {
        Member bot = memberService.getMemberByUsername(botUsername);
        if (chatRoomParticipantService.findActiveParticipant(room.getId(), bot.getId()).isPresent()) {
            return;
        }
        room.addParticipant(ChatParticipant.of(bot, room));
    }

    private ChatRoom findRoomAsOwner(Long roomId, Long memberId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
        Long ownerId = chatRoomParticipantService.findOwnerId(roomId);
        if (!ownerId.equals(memberId)) {
            throw new ChatRoomException(ChatRoomErrorCode.OWNER_PERMISSION_REQUIRED);
        }
        return room;
    }
}