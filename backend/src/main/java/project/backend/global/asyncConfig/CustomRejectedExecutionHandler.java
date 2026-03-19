package project.backend.global.asyncConfig;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

@Slf4j
@Component
public class CustomRejectedExecutionHandler implements RejectedExecutionHandler {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 100;

    private final Counter rejectedTaskCounter;

    public CustomRejectedExecutionHandler(MeterRegistry meterRegistry) {
        this.rejectedTaskCounter = meterRegistry.counter(
            "async_tasks_rejected_total",
            "executor", "chat-room"
        );
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        int attempt = 0;
        boolean submitted = false;

        while (attempt < MAX_RETRIES && !submitted) {
            if (attempt > 0) {
                long backoffDelay = BASE_DELAY_MS * (1L << (attempt - 1));
                long jitter = ThreadLocalRandom.current().nextLong(backoffDelay / 2);
                long sleepTime = backoffDelay + jitter;

                try {
                    log.warn("작업 큐 포화 - 재시도 {}회차, 대기 {}ms", attempt, sleepTime);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ChatRoomException(ChatRoomErrorCode.ASYNC_TASK_REJECTED);
                }
            }

            try {
                executor.execute(r);
                submitted = true;
            } catch (RejectedExecutionException e) {
                attempt++;
            }
        }

        if (!submitted) {
            rejectedTaskCounter.increment();
            log.error("작업 큐 가득 참 - 재시도 최종 실패");
            throw new ChatRoomException(ChatRoomErrorCode.ASYNC_TASK_REJECTED);
        }
    }
}
