package com.ratelimit.demo;

import com.ratelimit.algorithm.Algorithm;
import com.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @PostMapping("/order")
    @RateLimit(key = "#userId", limit = 5, window = "1m", algorithm = Algorithm.TOKEN_BUCKET)
    public Map<String, Object> createOrder(@RequestHeader("X-User-Id") String userId) {
        return Map.of("status", "ok", "user", userId, "msg", "order created");
    }

    @GetMapping("/search")
    @RateLimit(key = "#userId", limit = 10, window = "10s", algorithm = Algorithm.SLIDING_WINDOW_LOG)
    public Map<String, Object> search(@RequestHeader("X-User-Id") String userId) {
        return Map.of("status", "ok", "user", userId, "msg", "search done");
    }

    // global rate limit example — same bucket for everyone
    @GetMapping("/global")
    @RateLimit(key = "'global'", limit = 100, window = "1m", algorithm = Algorithm.FIXED_WINDOW)
    public Map<String, Object> globalEndpoint() {
        return Map.of("status", "ok");
    }
}
