package com.ratelimit.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handle(RateLimitExceededException ex) {
        long retryAfterSeconds = Math.max(1, ex.getRetryAfterMillis() / 1000);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .header("X-RateLimit-Limit", String.valueOf(ex.getLimit()))
                .header("X-RateLimit-Remaining", "0")
                .body(Map.of(
                        "error", "rate_limit_exceeded",
                        "message", "Too many requests, please retry after " + retryAfterSeconds + "s"
                ));
    }
}
