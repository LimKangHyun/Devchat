package project.api.domain.chat.chatroom.app;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.api.domain.chat.chatroom.dao.ChatRoomRedisRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomSequenceService {

    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final ChatRoomSyncService chatRoomSyncService;
    private final CheckpointReconstructor reconstructor;
    private final MeterRegistry meterRegistry;

    @CircuitBreaker(name = "redis", fallbackMethod = "incrementCacheFallback")
    public void incrementCache(Long roomId) {
        chatRoomRedisRepository.genMessageSeq(roomId);
    }

    public void incrementCacheFallback(Long roomId, Throwable e) {
        log.info("Redis 캐시 증분 스킵 - roomId={}", roomId);
        meterRegistry.counter("redis.fallback", "reason", "incrementCache").increment();
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getLatestSequenceFallback")
    public Long getLatestSequence(Long roomId) {
        Long redisSeq = chatRoomRedisRepository.getSequence(roomId);
        if (redisSeq == -1L) {
            return reconstructor.reconstruct(roomId);   // 캐시 미스 → 재구성
        }
        return redisSeq;
    }

    public Long getLatestSequenceFallback(Long roomId, Throwable e) {
        meterRegistry.counter("redis.fallback", "reason", "latestSequence").increment();
        return reconstructor.reconstruct(roomId);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getSequencesFallback")
    public Map<Long, Long> getSequences(List<Long> roomIds) {
        List<Long> sequences = chatRoomRedisRepository.getSequences(roomIds);

        Map<Long, Long> result = new HashMap<>();
        List<Long> missingRoomIds = new ArrayList<>();

        for (int i = 0; i < roomIds.size(); i++) {
            if (sequences.get(i) == null) {
                missingRoomIds.add(roomIds.get(i));
            } else {
                result.put(roomIds.get(i), sequences.get(i));
            }
        }

        // 목록 조회는 room이 다수이므로 개별 재구성 대신 checkpoint 값을 일괄 조회
        if (!missingRoomIds.isEmpty()) {
            Map<Long, Long> fromDb = chatRoomSyncService.getCumulativeCounts(missingRoomIds);
            chatRoomRedisRepository.bulkSetSequences(fromDb);
            result.putAll(fromDb);
        }
        return result;
    }

    public Map<Long, Long> getSequencesFallback(List<Long> roomIds, Throwable e) {
        meterRegistry.counter("redis.fallback", "reason", "sequence").increment();
        return chatRoomSyncService.getCumulativeCounts(roomIds);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getSortedRoomIdsFallback")
    public List<Long> getSortedRoomIds(List<Long> roomIds) {
        return chatRoomRedisRepository.getSortedRoomIds(roomIds);
    }

    public List<Long> getSortedRoomIdsFallback(List<Long> roomIds, Throwable e) {
        return roomIds;
    }
}