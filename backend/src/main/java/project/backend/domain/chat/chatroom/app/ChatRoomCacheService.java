package project.backend.domain.chat.chatroom.app;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatroom.app.limiter.FallbackLimiter;
import project.backend.domain.chat.chatroom.app.limiter.UserRateLimiter;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomCacheService {

    private final ChatRoomRedisService chatRoomRedisService;
    private final ChatRoomRepository chatRoomRepository;
    private final MeterRegistry meterRegistry;
    private final UserRateLimiter userRateLimiter;
    private final FallbackLimiter fallbackLimiter;

    // ===== 메시지 전송 시퀀스 =====

    @CircuitBreaker(name = "redis", fallbackMethod = "handleMessageDeliveryFallback")
    public Long handleMessageDelivery(Long roomId, Long userId) {
        if (!userRateLimiter.allow(userId)) {
            throw new ChatRoomException(ChatRoomErrorCode.TOO_MANY_REQUESTS);
        }
        return chatRoomRedisService.handleMessageDelivery(roomId);
    }

    public Long handleMessageDeliveryFallback(Long roomId, Long userId, CallNotPermittedException e) {
        log.warn("Circuit OPEN - handleMessageDelivery 차단 roomId={}", roomId);
        return doFallback(roomId, userId);
    }

    public Long handleMessageDeliveryFallback(Long roomId, Long userId, Throwable e) {
        log.warn("Redis 예외 - handleMessageDelivery 폴백 roomId={}", roomId);
        return doFallback(roomId, userId);
    }

    @Transactional
    public Long incrementSequenceFromDb(Long roomId) {
        chatRoomRepository.incrementSequence(roomId);
        return chatRoomRepository.findLastInsertId();
    }

    // ===== 시퀀스 조회 =====

    @CircuitBreaker(name = "redis", fallbackMethod = "getLatestSequenceFallback")
    public Long getLatestSequence(Long roomId) {
        Long redisSeq = chatRoomRedisService.getSequence(roomId);
        if (redisSeq == -1L) {
            ChatRoom room = chatRoomRepository.findById(roomId)
                    .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
            long dbSeq = room.getLastSequence();
            chatRoomRedisService.setSequence(roomId, dbSeq);
            return dbSeq;
        }
        return redisSeq;
    }

    public Long getLatestSequenceFallback(Long roomId, Throwable e) {
        log.warn("Circuit OPEN - getLatestSequence 폴백 roomId={}", roomId);
        meterRegistry.counter("redis.fallback", "reason", "latestSequence").increment();
        return chatRoomRepository.findById(roomId)
                .map(ChatRoom::getLastSequence)
                .orElse(0L);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getSequencesFallback")
    public List<Long> getSequences(List<Long> roomIds) {
        return chatRoomRedisService.getSequences(roomIds);
    }

    public List<Long> getSequencesFallback(List<Long> roomIds, Throwable e) {
        log.warn("Circuit OPEN - getSequences 폴백");
        meterRegistry.counter("redis.fallback", "reason", "sequence").increment();
        Map<Long, Long> seqMap = chatRoomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(
                        ChatRoom::getId,
                        ChatRoom::getLastSequence
                ));
        return roomIds.stream()
                .map(id -> seqMap.getOrDefault(id, 0L))
                .toList();
    }

    // ===== 채팅방 정렬 =====

    @CircuitBreaker(name = "redis", fallbackMethod = "getSortedRoomIdsFallback")
    public List<Long> getSortedRoomIds(List<Long> roomIds) {
        return chatRoomRedisService.getSortedRoomIds(roomIds);
    }

    public List<Long> getSortedRoomIdsFallback(List<Long> roomIds, Throwable e) {
        log.warn("Circuit OPEN - getSortedRoomIds 폴백");
        return roomIds;
    }

    private Long doFallback(Long roomId, Long userId) {
        meterRegistry.counter("redis.fallback", "method", "handleMessageDelivery").increment();

        if (!userRateLimiter.allow(userId)) {
            log.warn("Rate Limit 초과 - userId={}", userId);
            throw new ChatRoomException(ChatRoomErrorCode.TOO_MANY_REQUESTS);
        }

        if (!fallbackLimiter.tryAcquire()) {
            log.warn("Fallback 동시 처리 한도 초과 - DB 보호 roomId={}", roomId);
            meterRegistry.counter("redis.fallback.rejected").increment();
            throw new ChatRoomException(ChatRoomErrorCode.SERVICE_UNAVAILABLE);
        }

        try {
            return incrementSequenceFromDb(roomId);
        } finally {
            fallbackLimiter.release();
        }
    }
}