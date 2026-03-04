package project.backend.global.asyncConfig;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

@Slf4j
@Component
public class RejectExecutionHandler implements RejectedExecutionHandler {

    private final Counter rejectedTaskCounter;

    public RejectExecutionHandler(MeterRegistry meterRegistry) { // 거절 메트릭 수집
        this.rejectedTaskCounter = Counter.builder("async_tasks_rejected_total")
            .description("Total number of async tasks rejected due to thread pool saturation")
            .register(meterRegistry);
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        int maxRetries = 3;
        long retryDelayMillis = 100; // (ms 기준) 보톤 50~200으로 잡음

        int attempt = 0;
        boolean submitted = false;

        while (attempt < maxRetries && !submitted) {
            try {
                if (attempt > 0) {
                    log.warn("작업 큐 포화로 인해 재시도 중: {}번째 시도", attempt);
                    Thread.sleep(retryDelayMillis);
                }
                executor.execute(r);
                submitted = true;
            } catch (RejectedExecutionException e) {
                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ChatRoomException(ChatRoomErrorCode.ASYNC_TASK_REJECTED);
            }
        }

        if (!submitted) {
            rejectedTaskCounter.increment();
            log.error("작업 큐가 가득차 작업 거부됨 - 재시도 실패");
            throw new ChatRoomException(ChatRoomErrorCode.ASYNC_TASK_REJECTED);
        }
    }
}
