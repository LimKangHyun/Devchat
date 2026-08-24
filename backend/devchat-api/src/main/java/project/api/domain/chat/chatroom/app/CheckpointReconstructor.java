package project.api.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 동일 room에 대한 동시 재구성 요청을 인스턴스 내에서 1건으로 병합한다.
 * 인스턴스 간 중복은 CheckpointWriter의 FOR UPDATE가 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckpointReconstructor {

    private final CheckpointWriter checkpointWriter;
    private final Map<Long, CompletableFuture<Long>> inFlight = new ConcurrentHashMap<>();

    public Long reconstruct(Long roomId) {
        return inFlight.computeIfAbsent(roomId, id ->
                CompletableFuture
                        .supplyAsync(() -> checkpointWriter.reconstruct(id))
                        .whenComplete((r, e) -> inFlight.remove(id))
        ).join();
    }
}