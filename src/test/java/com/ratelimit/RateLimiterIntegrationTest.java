package com.ratelimit;

import com.ratelimit.algorithm.Algorithm;
import com.ratelimit.core.RateLimitResult;
import com.ratelimit.core.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class RateLimiterIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired RateLimiter rateLimiter;
    @Autowired StringRedisTemplate redisTemplate;

    private String key;

    @BeforeEach
    void freshKey() {
        // unique key per test so they don't interfere
        key = "test-" + UUID.randomUUID();
    }

    @Test
    void tokenBucket_allowsUpToLimit() {
        for (int i = 0; i < 5; i++) {
            RateLimitResult r = rateLimiter.tryAcquire(key, 5, Duration.ofMinutes(1), Algorithm.TOKEN_BUCKET);
            assertTrue(r.allowed(), "request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void tokenBucket_blocksAfterLimit() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(key, 5, Duration.ofMinutes(1), Algorithm.TOKEN_BUCKET);
        }
        RateLimitResult r = rateLimiter.tryAcquire(key, 5, Duration.ofMinutes(1), Algorithm.TOKEN_BUCKET);
        assertFalse(r.allowed());
        assertTrue(r.retryAfterMillis() > 0);
    }

    @Test
    void slidingWindow_resetsAfterWindowExpires() throws InterruptedException {
        rateLimiter.tryAcquire(key, 2, Duration.ofSeconds(1), Algorithm.SLIDING_WINDOW_LOG);
        rateLimiter.tryAcquire(key, 2, Duration.ofSeconds(1), Algorithm.SLIDING_WINDOW_LOG);

        RateLimitResult blocked = rateLimiter.tryAcquire(key, 2, Duration.ofSeconds(1), Algorithm.SLIDING_WINDOW_LOG);
        assertFalse(blocked.allowed());

        Thread.sleep(1100);

        RateLimitResult afterWait = rateLimiter.tryAcquire(key, 2, Duration.ofSeconds(1), Algorithm.SLIDING_WINDOW_LOG);
        assertTrue(afterWait.allowed());
    }

    @Test
    void differentKeysHaveSeparateBuckets() {
        RateLimitResult a1 = rateLimiter.tryAcquire("user-A", 1, Duration.ofMinutes(1), Algorithm.TOKEN_BUCKET);
        RateLimitResult a2 = rateLimiter.tryAcquire("user-A", 1, Duration.ofMinutes(1), Algorithm.TOKEN_BUCKET);
        RateLimitResult b1 = rateLimiter.tryAcquire("user-B", 1, Duration.ofMinutes(1), Algorithm.TOKEN_BUCKET);

        assertTrue(a1.allowed());
        assertFalse(a2.allowed());   // user A is rate-limited
        assertTrue(b1.allowed());    // user B is not
    }

    @Test
    void fixedWindow_correctRemainingCount() {
        RateLimitResult r1 = rateLimiter.tryAcquire(key, 3, Duration.ofSeconds(10), Algorithm.FIXED_WINDOW);
        RateLimitResult r2 = rateLimiter.tryAcquire(key, 3, Duration.ofSeconds(10), Algorithm.FIXED_WINDOW);

        assertEquals(2, r1.remaining());
        assertEquals(1, r2.remaining());
    }
}
