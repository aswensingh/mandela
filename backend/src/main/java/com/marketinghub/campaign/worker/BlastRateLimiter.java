package com.marketinghub.campaign.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Per-tenant token bucket backed by a Lua script in Redis.
 *
 *   capacity:        bucket size (e.g. 80)
 *   refillPerSecond: tokens added per second (e.g. 80 -> sustained 80/sec)
 *
 * The Lua script returns 0 if a token was granted, or the suggested wait-ms
 * until one would become available. {@link #acquireBlocking(UUID)} loops
 * (sleeping the suggested duration) until the script grants a token, so the
 * caller is rate-limited without busy-waiting.
 */
@Component
public class BlastRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(BlastRateLimiter.class);
    private static final String KEY_PREFIX = "blast:rate:";

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script;
    private final int capacity;
    private final int refillPerSecond;

    public BlastRateLimiter(
        StringRedisTemplate redis,
        @Value("${blast.rate-limit.capacity:80}") int capacity,
        @Value("${blast.rate-limit.refill-per-second:80}") int refillPerSecond
    ) {
        this.redis = redis;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource("rate-limit-token-bucket.lua")));
        s.setResultType(Long.class);
        this.script = s;
    }

    /** Returns 0 if granted, else the suggested wait-ms until a token is available. */
    public long tryAcquireOrWaitMs(UUID tenantId) {
        Long out = redis.execute(
            script,
            List.of(KEY_PREFIX + tenantId),
            String.valueOf(capacity),
            String.valueOf(refillPerSecond),
            String.valueOf(System.currentTimeMillis()));
        return out == null ? 0L : out;
    }

    /** Block (sleep-loop) until a token is granted. */
    public void acquireBlocking(UUID tenantId) throws InterruptedException {
        while (true) {
            long wait = tryAcquireOrWaitMs(tenantId);
            if (wait <= 0) return;
            // Cap the sleep so concurrent consumers re-check often enough to stay smooth.
            long sleep = Math.min(wait, 200L);
            if (log.isDebugEnabled()) {
                log.debug("Rate-limited for tenant {} — sleeping {}ms (script said {}ms)", tenantId, sleep, wait);
            }
            Thread.sleep(sleep);
        }
    }

    public int capacity() { return capacity; }
    public int refillPerSecond() { return refillPerSecond; }
}
