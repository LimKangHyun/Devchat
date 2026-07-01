package project.api.global.ratelimit;

public class TokenBucket {

    private final int capacity;
    private final int refillRate;
    private final int cooldownSeconds;
    private double tokens;
    private long lastRefillTime;
    private long lastAccessTime;
    private long cooldownUntil = 0;

    public TokenBucket(int capacity, int refillRate, int cooldownSeconds) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.cooldownSeconds = cooldownSeconds;
        this.tokens = capacity;
        this.lastRefillTime = System.currentTimeMillis();
        this.lastAccessTime = System.currentTimeMillis();
    }

    public synchronized boolean tryConsume(int cost) {
        long now = System.currentTimeMillis();
        lastAccessTime = now;
        if (now < cooldownUntil) return false;
        double elapsed = (now - lastRefillTime) / 1000.0;
        tokens = Math.min(capacity, tokens + elapsed * refillRate);
        lastRefillTime = now;
        if (tokens < cost) {
            cooldownUntil = now + (cooldownSeconds * 1000L);
            return false;
        }
        tokens -= cost;
        return true;
    }

    public boolean isExpired(long now, long ttlMs) {
        return (now - lastAccessTime) > ttlMs;
    }
}