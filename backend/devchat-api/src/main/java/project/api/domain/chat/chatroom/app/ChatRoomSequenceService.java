package project.api.domain.chat.chatroom.app;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
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
    private final ChatMessageRepository chatMessageRepository;

    @CircuitBreaker(name = "redis", fallbackMethod = "incrementCacheFallback")
    public void incrementCache(Long roomId) {
        Long seq = chatRoomRedisRepository.genMessageSeq(roomId);
        if (seq == null || seq != -1L) return;
        // 번호가 없으면(새 방, Redis가 비워진 뒤) 예전에는 그냥 넘어가 안 읽은 수가 비거나 몇 분씩 늦었다.
        // 커밋된 메시지 수로 채우고 올린다. 방금 저장된 메시지도 개수에 들어 있으니 하나 적게 채운다
        long saved = chatMessageRepository.countByChatRoom_Id(roomId);
        chatRoomRedisRepository.setSequenceIfGreater(roomId, Math.max(0, saved - 1));
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
            chatRoomRedisRepository.bulkSetSequencesIfGreater(fromDb);
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