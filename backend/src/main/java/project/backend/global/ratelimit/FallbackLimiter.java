package project.backend.global.ratelimit;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class FallbackLimiter {

    private static final int MAX_CONCURRENT_FALLBACK = 7; // DB 커넥션 풀의 70%
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_FALLBACK);

    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    public void release() {
        semaphore.release();
    }
}