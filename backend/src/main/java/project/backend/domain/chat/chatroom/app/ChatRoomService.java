package project.backend.domain.chat.chatroom.app;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomAlarmRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.dto.AllRoomsResponse;
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
import project.backend.domain.chat.chatroom.entity.ChatRoomAlarm;
import project.backend.domain.chat.chatroom.mapper.ChatRoomMapper;
import project.backend.domain.chat.github.app.GitMessageService;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;
import project.backend.global.metric.TimeTrace;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatRoomMapper chatRoomMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final MemberService memberService;
    private final GitMessageService gitMessageService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomAlarmRepository chatRoomAlarmRepository;

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${github.username}")
    private String githubUsername;

    @Transactional
    public ChatRoomSimpleResponse createChatRoom(ChatRoomRequest request, Long ownerId) {
        Member owner = memberService.getMemberById(ownerId);

        ChatRoom chatRoom = chatRoomMapper.toEntity(request);

        ChatParticipant chatParticipant = ChatParticipant.createOwner(owner, chatRoom);
        chatRoom.addParticipant(chatParticipant);

        ChatRoom savedRoom = chatRoomRepository.save(chatRoom);

        createAlarm(ownerId, chatRoom.getId());

        if (!request.getRepositoryUrl().isBlank()) {
            gitMessageService.registerWebhook(request.getRepositoryUrl(),
                savedRoom.getId(), owner.getId()); //웹훅 자동 등록
            joinGitHubBot(savedRoom); //깃허브봇 채팅 참가
        }

        updateRoomMembersCache(savedRoom.getId());
        return chatRoomMapper.toSimpleResponse(savedRoom, owner);
    }

    private void createAlarm(Long memberId, Long roomId) {
        ChatRoomAlarm alarm = new ChatRoomAlarm(memberId, roomId);
        chatRoomAlarmRepository.save(alarm);
    }

    private void joinGitHubBot(ChatRoom room) {
        Member githubBot = memberService.getMemberByUsername(githubUsername);
        ChatParticipant gitParticipant = ChatParticipant.of(githubBot, room);
        room.addParticipant(gitParticipant);
    }

    @TimeTrace
    @Transactional
    public InviteJoinResponse joinChatRoom(String inviteCode, Long memberId) {
        log.info("timetrace 적용");
        ChatRoom room = getByInviteCode(inviteCode);
        Member member = memberService.getMemberById(memberId);

        handleParticipantJoin(room, member);
        createAlarm(memberId, room.getId());

        ChatMessage message = chatMessageMapper.toEntityWithJoinEvent(room, member,
            LocalDateTime.now());
        ChatMessage savedMessage = chatMessageRepository.save(message);

        eventPublisher.publishEvent(
            new JoinChatRoomEvent(room.getId(), memberId, member.getNickname(),
                savedMessage.getId(), savedMessage.getSendAt()));

        updateRoomMembersCache(room.getId());
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

        validateParticipant(memberId, roomId);

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

        updateRoomMembersCache(roomId);
        updateRecentRoomAfterLeaving(memberId);
    }

    private void updateRecentRoomAfterLeaving(Long memberId) {
        Member member = memberService.getMemberById(memberId);

        Optional<ChatParticipant> mostRecentActiveRoom =
            chatParticipantRepository.findTopByParticipantIdAndIsActiveTrueOrderByJoinAtDesc(
                memberId);

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
        validateParticipant(memberId, room.getId());

        syncAndResetUnreadCount(room.getId(), memberId);

        memberService.getMemberById(memberId).setRecentRoomId(room.getId());

        Long ownerId = findOwnerId(room.getId());
        boolean alarmEnable = chatRoomAlarmRepository
            .findEnabledByMemberIdAndRoomId(memberId, room.getId());

        return new EntryRoomResponse(room.getId(), room.getName(), ownerId, alarmEnable);
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

    public void validateParticipant(Long memberId, Long roomId) {
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

        gitMessageService.deleteWebhook(room, memberId);

        eventPublisher.publishEvent(
            new DeleteChatRoomEvent(roomId, room.getName())
        );

        chatRoomRepository.delete(room);
    }

    @Transactional
    public boolean toggleAlarm(Long roomId, Long memberId) {
        ChatRoomAlarm alarm = chatRoomAlarmRepository.findByIdMemberIdAndIdRoomId(memberId, roomId)
            .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.ALARM_NOT_FOUND));

        System.out.println("Before toggle: " + alarm.isEnabled());
        alarm.setEnabled(!alarm.isEnabled());
        System.out.println("After toggle: " + alarm.isEnabled());

        return alarm.isEnabled();
    }

    @Transactional(readOnly = true)
    public List<AllRoomsResponse> findAllRoomsByMemberId(Long memberId) {
        List<ChatRoom> chatRooms = chatRoomRepository.findAllRoomsByParticipantId(memberId);

        List<Long> roomIds = chatRooms.stream()
            .map(ChatRoom::getId)
            .toList();

        Map<Long, Boolean> alarmEnabledMap = chatRoomAlarmRepository
            .findEnabledMap(memberId, roomIds);

        // DB unreadCount map
        Map<Long, Long> dbUnreadCountMap = chatParticipantRepository
            .findAllByParticipantIdAndIsActiveTrue(memberId)
            .stream()
            .collect(Collectors.toMap(
                cp -> cp.getChatRoom().getId(),
                ChatParticipant::getUnreadCount
            ));

        return chatRooms.stream()
            .map(room -> {
                boolean alarmEnabled = alarmEnabledMap.getOrDefault(room.getId(), true);
                long dbCount = dbUnreadCountMap.getOrDefault(room.getId(), 0L);

                long redisCount = 0L;
                try {
                    String redisValue = redisTemplate.opsForValue()
                        .get("unread:" + room.getId() + ":" + memberId);
                    redisCount = redisValue != null ? Long.parseLong(redisValue) : 0L;
                } catch (Exception e) {
                    log.warn("Redis GET 실패 - DB 값만 사용. roomId: {}", room.getId());
                }

                long unreadCount = dbCount + redisCount;
                return new AllRoomsResponse(room.getId(), room.getInviteCode(), room.getName(),
                    alarmEnabled, unreadCount);
            }).toList();
    }

    private long getUnreadCount(Long roomId, Long memberId, long dbFallback) {
        try {
            String redisValue = (String) redisTemplate.opsForValue()
                .get("unread:" + roomId + ":" + memberId);
            return redisValue != null ? Long.parseLong(redisValue) : dbFallback;
        } catch (Exception e) {
            log.warn("Redis GET 실패 - DB 폴백. roomId: {}, memberId: {}", roomId, memberId);
            return dbFallback;
        }
    }

    @Async("unreadCountExecutor")
    public void incrementUnreadCount(Long roomId, Long senderId) {
        Set<String> members = redisTemplate.opsForSet().members("room:members:" + roomId);

        if (members == null || members.isEmpty()) {
            updateRoomMembersCache(roomId);
            members = redisTemplate.opsForSet().members("room:members:" + roomId);
        }

        if (members == null) {
            return;
        }

        List<String> targetMembers = members.stream()
            .filter(memberId -> !memberId.equals(senderId.toString()))
            .toList();

        // Pipeline으로 한 번에 처리
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            targetMembers.forEach(memberId -> {
                String key = "unread:" + roomId + ":" + memberId;
                connection.stringCommands().incr(key.getBytes());
            });
            return null;
        });
    }

    @Transactional
    public void markAsRead(Long roomId, Long memberId) {
        syncAndResetUnreadCount(roomId, memberId);
        log.info("Marked as read: {}", memberId);
    }

    private void syncAndResetUnreadCount(Long roomId, Long memberId) {
        chatParticipantRepository
            .findByChatRoomIdAndParticipantIdAndIsActiveTrue(roomId, memberId)
            .ifPresent(p -> {
                Long latestMessageId = chatMessageRepository
                    .findTopByChatRoom_IdOrderByIdDesc(roomId)
                    .map(ChatMessage::getId)
                    .orElse(null);

                try {
                    // GETDEL로 원자적으로 읽고 삭제
                    String redisValue = redisTemplate.opsForValue()
                        .getAndDelete("unread:" + roomId + ":" + memberId);
                    // Redis 값은 버림 (어차피 DB 0으로 초기화)
                } catch (Exception e) {
                    log.warn("Redis GETDEL 실패. roomId: {}, memberId: {}", roomId, memberId);
                }

                p.resetUnreadCount(latestMessageId);  // DB unread_count = 0
            });
    }

    private void updateRoomMembersCache(Long roomId) {
        ChatRoom room = getRoomById(roomId);
        List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(room);

        String key = "room:members:" + roomId;
        redisTemplate.delete(key);

        if (!participants.isEmpty()) {
            String[] memberIds = participants.stream()
                .map(p -> p.getParticipant().getId().toString())
                .toArray(String[]::new);
            redisTemplate.opsForSet().add(key, memberIds);
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
        }
    }
}