package com.ratelimit.core;

import com.ratelimit.algorithm.Algorithm;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "rl:";

    private final StringRedisTemplate redis;

    private String tokenBucketScript;
    private String slidingWindowScript;
    private String fixedWindowScript;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @PostConstruct
    public void loadScripts() {
        this.tokenBucketScript = readScript("scripts/token_bucket.lua");
        this.slidingWindowScript = readScript("scripts/sliding_window.lua");
        this.fixedWindowScript = readScript("scripts/fixed_window.lua");
    }

    @Override
    public RateLimitResult tryAcquire(String key, int limit, Duration window, Algorithm algorithm) {
        String fullKey = KEY_PREFIX + algorithm.name().toLowerCase() + ":" + key;
        long now = System.currentTimeMillis();

        List<Long> result = switch (algorithm) {
            case TOKEN_BUCKET -> runTokenBucket(fullKey, limit, window, now);
            case SLIDING_WINDOW_LOG -> runSlidingWindow(fullKey, limit, window, now);
            case FIXED_WINDOW -> runFixedWindow(fullKey, limit, window, now);
        };

        if (result == null || result.size() < 3) {
            log.warn("Redis returned unexpected response for key {}: {}", fullKey, result);
            // fail open — don't block traffic if Redis behaves weird
            return RateLimitResult.allowed(limit);
        }

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        long retryAfter = result.get(2);

        return allowed ? RateLimitResult.allowed(remaining) : RateLimitResult.denied(retryAfter);
    }

    private List<Long> runTokenBucket(String key, int limit, Duration window, long now) {
        // refill rate = limit tokens per window
        double refillPerMs = (double) limit / window.toMillis();
        // we encode refillPerMs * 1000 as a long to keep Lua arithmetic simple
        long refillNumerator = (long) (refillPerMs * 1_000_000);
        return execute(tokenBucketScript, key,
                String.valueOf(limit),
                String.valueOf(refillNumerator),
                String.valueOf(now),
                "1");
    }

    private List<Long> runSlidingWindow(String key, int limit, Duration window, long now) {
        return execute(slidingWindowScript, key,
                String.valueOf(limit),
                String.valueOf(window.toMillis()),
                String.valueOf(now));
    }

    private List<Long> runFixedWindow(String key, int limit, Duration window, long now) {
        return execute(fixedWindowScript, key,
                String.valueOf(limit),
                String.valueOf(window.toMillis()),
                String.valueOf(now));
    }

    @SuppressWarnings("unchecked")
    private List<Long> execute(String script, String key, String... args) {
        byte[][] keys = new byte[][] { key.getBytes(StandardCharsets.UTF_8) };
        byte[][] argBytes = new byte[args.length][];
        for (int i = 0; i < args.length; i++) {
            argBytes[i] = args[i].getBytes(StandardCharsets.UTF_8);
        }

        return redis.execute(connection -> (List<Long>) connection.scriptingCommands()
                .eval(script.getBytes(StandardCharsets.UTF_8),
                        ReturnType.MULTI, 1,
                        concat(keys, argBytes)),
                true);
    }

    private byte[][] concat(byte[][] a, byte[][] b) {
        byte[][] r = new byte[a.length + b.length][];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private String readScript(String path) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(path).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load lua script: " + path, e);
        }
    }
}
