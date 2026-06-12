package com.ratelimit.core;

import com.ratelimit.algorithm.Algorithm;

import java.time.Duration;

public interface RateLimiter {

    RateLimitResult tryAcquire(String key, int limit, Duration window, Algorithm algorithm);
}
