package project.ai.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${indexing.executor-type:platform}")
    private String executorType;

    private static final int BATCH_CONCURRENCY = 4;

    @Bean(name = "repoIndexingExecutor")
    public Executor getRepoIndexingExecutor() {
        if ("virtual".equalsIgnoreCase(executorType)) {
            log.info("repoIndexingExecutor: Virtual Thread 사용");
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        log.info("repoIndexingExecutor: 플랫폼 스레드 풀 사용");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("RepoIndexing-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "batchIndexingExecutor")
    public ExecutorService getBatchIndexingExecutor() {
        if ("virtual".equalsIgnoreCase(executorType)) {
            log.info("batchIndexingExecutor: Virtual Thread 사용");
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        log.info("batchIndexingExecutor: 플랫폼 스레드 고정 풀 사용 (size={})", BATCH_CONCURRENCY);
        return Executors.newFixedThreadPool(
            BATCH_CONCURRENCY,
            r -> {
                Thread t = new Thread(r, "BatchIndexing-" + Thread.currentThread().threadId());
                t.setDaemon(true);
                return t;
            }
        );
    }
}