package project.backend.domain.chat.chatmessage.app.policy.limiter;

public class TokenBucket {

    private final int capacity;
    private final int refillRate;
    private double tokens;
    private long lastRefillTime;
    private long lastAccessTime;

    public TokenBucket(int capacity, int refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefillTime = System.currentTimeMillis();
        this.lastAccessTime = System.currentTimeMillis();
    }

    public synchronized boolean tryConsume(int cost) {
        long now = System.currentTimeMillis();
        lastAccessTime = now;
        double elapsed = (now - lastRefillTime) / 1000.0;
        tokens = Math.min(capacity, tokens + elapsed * refillRate);
        lastRefillTime = now;
        if (tokens < cost) return false;
        tokens -= cost;
        return true;
    }

    public boolean isExpired(long now, long ttlMs) {
        return (now - lastAccessTime) > ttlMs;
    }
}