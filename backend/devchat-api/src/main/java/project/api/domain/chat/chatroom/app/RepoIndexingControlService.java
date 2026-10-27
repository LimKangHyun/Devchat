package project.api.domain.chat.chatroom.app;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.dto.IndexingStatusResponse;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.chat.chatroom.event.ChatRoomCreatedEvent;
import project.api.global.exception.errorcode.ChatRoomErrorCode;
import project.api.global.exception.ex.ChatRoomException;
import project.api.global.redis.RedisStreamClient;
import project.common.enums.IndexingStatus;

/**
 * 저장소 인덱싱 조회·취소·다시 시작. 인덱싱 자체는 AI 서버가 하고, 여기서는 요청과 상태만 다룬다.
 * 취소·다시 시작은 방장만 할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepoIndexingControlService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantService chatRoomParticipantService;
    private final RedisStreamClient redisStreamClient;
    private final IndexingStatusBroadcaster broadcaster;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 참여자 누구나 볼 수 있다. 진행 중이면 AI 서버가 기록한 단계·진행률을 함께 준다.
     * RUNNING인데 AI 서버의 락과 요청 표시가 모두 없으면, 서버가 비정상 종료돼 결과를 보내지 못한 것이라
     * 이 자리에서 FAILED로 정리한다. 별도 스케줄러 없이, 누군가 방에 들어와 상태를 볼 때 정리된다.
     */
    @Transactional
    public IndexingStatusResponse getStatus(Long roomId, Long memberId) {
        chatRoomParticipantService.validateParticipant(memberId, roomId);
        ChatRoom room = getRoom(roomId);
        if (room.getIndexingStatus() == IndexingStatus.RUNNING && !redisStreamClient.isIndexingInProgress(roomId)) {
            log.warn("RUNNING 상태지만 AI 서버 인덱싱 락이 없음, FAILED로 정리. roomId={}", roomId);
            room.updateIndexingStatus(IndexingStatus.FAILED);
            broadcaster.send(roomId, IndexingStatusResponse.finished(IndexingStatus.FAILED.name()));
        }
        if (room.getIndexingStatus() != IndexingStatus.RUNNING) {
            return IndexingStatusResponse.finished(room.getIndexingStatus().name());
        }
        Map<Object, Object> progress = redisStreamClient.findIndexingProgress(roomId);
        return IndexingStatusResponse.of(IndexingStatus.RUNNING.name(),
            (String) progress.get("phase"), toInt(progress.get("done")), toInt(progress.get("total")));
    }

    /**
     * 진행 중인 인덱싱을 취소한다. AI 서버가 멈추고 청크를 지운 뒤 결과를 보내면 상태가 바뀐다.
     * AI 서버가 비정상 종료돼 RUNNING만 남은 경우에는 기다릴 대상이 없으므로 바로 취소 처리한다.
     */
    @Transactional
    public void cancel(Long roomId, Long memberId) {
        ChatRoom room = findRoomAsOwner(roomId, memberId);
        if (room.getIndexingStatus() != IndexingStatus.RUNNING) {
            throw new ChatRoomException(ChatRoomErrorCode.INDEXING_NOT_RUNNING);
        }
        if (!redisStreamClient.isIndexingInProgress(roomId)) {
            log.warn("RUNNING 상태지만 AI 서버 인덱싱 락이 없음, 바로 취소 처리. roomId={}", roomId);
            room.updateIndexingStatus(IndexingStatus.NONE);
            broadcaster.send(roomId, IndexingStatusResponse.finished("CANCELLED"));
            return;
        }
        redisStreamClient.requestIndexingCancel(roomId);
        log.info("인덱싱 취소 요청. roomId={}, memberId={}", roomId, memberId);
    }

    /** 취소·실패한 인덱싱을 처음부터 다시 시작한다. 인덱싱 요청은 커밋 후에 보낸다(ChatRoomEventListener). */
    @Transactional
    public IndexingStatusResponse restart(Long roomId, Long memberId) {
        ChatRoom room = findRoomAsOwner(roomId, memberId);
        if (room.getRepositoryUrl() == null || room.getRepositoryUrl().isBlank()) {
            throw new ChatRoomException(ChatRoomErrorCode.REPOSITORY_NOT_CONNECTED);
        }
        if (room.getIndexingStatus() == IndexingStatus.RUNNING && redisStreamClient.isIndexingInProgress(roomId)) {
            throw new ChatRoomException(ChatRoomErrorCode.INDEXING_ALREADY_RUNNING);
        }
        room.updateIndexingStatus(IndexingStatus.RUNNING);
        eventPublisher.publishEvent(new ChatRoomCreatedEvent(roomId, room.getRepositoryUrl(), memberId));
        log.info("인덱싱 다시 시작. roomId={}, memberId={}", roomId, memberId);

        IndexingStatusResponse response = IndexingStatusResponse.of(IndexingStatus.RUNNING.name(), "CLONING", 0, 0);
        broadcaster.send(roomId, response);
        return response;
    }

    private ChatRoom getRoom(Long roomId) {
        return chatRoomRepository.findById(roomId)
            .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    }

    private ChatRoom findRoomAsOwner(Long roomId, Long memberId) {
        ChatRoom room = getRoom(roomId);
        if (!chatRoomParticipantService.findOwnerId(roomId).equals(memberId)) {
            throw new ChatRoomException(ChatRoomErrorCode.OWNER_PERMISSION_REQUIRED);
        }
        return room;
    }

    private static int toInt(Object value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
