package project.backend.global.AsyncConfig;

import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig {

    private final RejectExecutionHandler rejectExecutionHandler;

    @Bean(name = "chatRoomEventExecutor")
    public Executor getChatRoomEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // 최소 스레드 수
        executor.setMaxPoolSize(20); // 최대 스레드 수
        executor.setQueueCapacity(100); // 작업 대기열
        executor.setThreadNamePrefix("ChatRoomEvent-");
        executor.setRejectedExecutionHandler(rejectExecutionHandler);
        executor.initialize();
        return executor;
    }
}
