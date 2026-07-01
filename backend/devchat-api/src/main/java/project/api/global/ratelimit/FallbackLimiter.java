package project.api.global.ratelimit;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class FallbackLimiter {

    private static final int MAX_CONCURRENT_FALLBACK = 4;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_FALLBACK);

    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    public void release() {
        semaphore.release();
    }
}