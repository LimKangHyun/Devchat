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

    /**
     * RAG 구조 검색(Pinecone 메타데이터 필터 쿼리) 전용 executor.
     *
     * repoIndexingExecutor/batchIndexingExecutor와 워크로드 성격이 다르다:
     *  - 인덱싱은 배치성 작업이라 동시성을 낮게 눌러도(4) 무방하지만,
     *  - 구조 검색은 PR 리뷰 요청 하나당 최대 20개 쿼리를 "그 요청 안에서" 병렬로
     *    끝내야 하는 요청-지연시간(latency) 경로다. 인덱싱과 풀을 공유하면
     *    배치 작업이 리뷰 요청의 지연시간에 영향을 줄 수 있어 분리한다.
     *
     * 같은 executor-type 프로퍼티를 재사용해 플랫폼/가상 스레드를 한 곳에서 전환한다.
     */
    @Bean(name = "structuralSearchExecutor")
    public ExecutorService getStructuralSearchExecutor() {
        if ("virtual".equalsIgnoreCase(executorType)) {
            log.info("structuralSearchExecutor: Virtual Thread 사용");
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        log.info("structuralSearchExecutor: 플랫폼 스레드 풀 사용");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 요청 하나당 최대 ~20개 필터 쿼리가 동시에 뜬다. 플랫폼 스레드로 돌릴 경우
        // 이 정도 동시성은 필요하지만, PR 파일 여러 개가 겹치면 스레드 수가 배로 늘어날 수 있어
        // 상한을 둔다. (Virtual Thread면 이 제약 자체가 사라지는 게 핵심 이점이다)
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("StructuralSearch-");
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }
}