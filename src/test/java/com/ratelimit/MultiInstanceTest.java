package com.ratelimit;

import com.ratelimit.algorithm.Algorithm;
import com.ratelimit.core.RateLimitResult;
import com.ratelimit.core.RedisRateLimiter;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class MultiInstanceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired StringRedisTemplate template;

    @Test
    void twoLimitersShareRedisState() {
        // Simulate two app instances by creating two RedisRateLimiter beans
        // backed by the same Redis. Both should see the same counter.
        RedisRateLimiter instanceA = new RedisRateLimiter(template);
        RedisRateLimiter instanceB = new RedisRateLimiter(template);
        instanceA.loadScripts();
        instanceB.loadScripts();

        String key = "shared-" + UUID.randomUUID();

        // 3 requests on instance A
        for (int i = 0; i < 3; i++) {
            assertTrue(instanceA.tryAcquire(key, 5, Duration.ofMinutes(1), Algorithm.TOKEN_BUCKET).allowed());
        }

        // 2 more on instance B — should succeed (total 5)
        assertTrue(instanceB.tryAcquire(key, 5, Duration.ofMinutes(1), Algorithm.TOKEN_BUCKET).allowed());
        assertTrue(instanceB.tryAcquire(key, 5, Duration.ofMinutes(1), Algorithm.TOKEN_BUCKET).allowed());

        // 6th request on instance A should be blocked
        // because B already consumed the remaining tokens
        RateLimitResult result = instanceA.tryAcquire(key, 5, Duration.ofMinutes(1), Algorithm.TOKEN_BUCKET);
        assertFalse(result.allowed(), "instance A should see instance B's consumption");
    }
}
