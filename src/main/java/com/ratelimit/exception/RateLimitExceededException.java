package com.ratelimit.exception;

public class RateLimitExceededException extends RuntimeException {

    private final String key;
    private final int limit;
    private final long retryAfterMillis;

    public RateLimitExceededException(String key, int limit, long retryAfterMillis) {
        super("Rate limit exceeded for key=" + key);
        this.key = key;
        this.limit = limit;
        this.retryAfterMillis = retryAfterMillis;
    }

    public String getKey() { return key; }
    public int getLimit() { return limit; }
    public long getRetryAfterMillis() { return retryAfterMillis; }
}
