package project.backend.domain.chat.chatroom.app;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.backend.domain.chat.chatmessage.app.policy.MessageSendPolicySelector;
import project.backend.domain.chat.chatmessage.app.policy.limiter.FallbackLimiter;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.global.exception.errorcode.ChatMessageErrorCode;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatMessageException;
import project.backend.global.exception.ex.ChatRoomException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomSequenceService {

    private final ChatRoomRedisService chatRoomRedisService;
    private final ChatRoomSyncService chatRoomSyncService;
    private final ChatRoomRepository chatRoomRepository;
    private final MeterRegistry meterRegistry;
    private final MessageSendPolicySelector policySelector;
    private final FallbackLimiter fallbackLimiter;
    private final CircuitBreakerRegistry registry;

    @CircuitBreaker(name = "redis", fallbackMethod = "genMessageSeqFallback")
    public Long genMessageSeq(Long roomId, Long userId) {
        log.info("genMessageSeq 진입 - circuitBreaker state={}",
                registry.circuitBreaker("redis").getState());
        if (!policySelector.select().canSend(userId)) {
            log.info("레이트 리밋 초과 - 예외 던짐 userId={}", userId);
            throw new ChatMessageException(ChatMessageErrorCode.TOO_MANY_REQUESTS);
        }
        return chatRoomRedisService.genMessageSeq(roomId);
    }

    public Long genMessageSeqFallback(Long roomId, Long userId, CallNotPermittedException e) {
        log.warn("Circuit OPEN - genMessageSeq 차단 roomId={}", roomId);
        if (!policySelector.select().canSend(userId)) {
            log.warn("Circuit OPEN - RateLimit 초과 userId={}", userId);
            throw new ChatMessageException(ChatMessageErrorCode.TOO_MANY_REQUESTS);
        }
        return doFallback(roomId, userId);
    }

    public Long genMessageSeqFallback(Long roomId, Long userId, Throwable e) {
        if (e instanceof ChatMessageException) {
            throw (ChatMessageException) e;
        }
        log.warn("Redis 예외 - genMessageSeq 폴백 roomId={} 예외={}", roomId, e.getMessage());
        return doFallback(roomId, userId);
    }

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

    @CircuitBreaker(name = "redis", fallbackMethod = "getSortedRoomIdsFallback")
    public List<Long> getSortedRoomIds(List<Long> roomIds) {
        return chatRoomRedisService.getSortedRoomIds(roomIds);
    }

    public List<Long> getSortedRoomIdsFallback(List<Long> roomIds, Throwable e) {
        log.warn("Circuit OPEN - getSortedRoomIds 폴백");
        return roomIds;
    }

    private Long doFallback(Long roomId, Long userId) {
        meterRegistry.counter("redis.fallback", "method", "genMessageSeq").increment();
        if (!fallbackLimiter.tryAcquire()) {
            log.warn("Fallback 동시 처리 한도 초과 - DB 보호 roomId={}", roomId);
            meterRegistry.counter("redis.fallback.rejected").increment();
            throw new ChatRoomException(ChatRoomErrorCode.SERVICE_UNAVAILABLE);
        }
        try {
            return chatRoomSyncService.incrementSequenceFromDb(roomId);
        } finally {
            fallbackLimiter.release();
        }
    }
}