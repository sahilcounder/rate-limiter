package com.ratelimit.annotation;

import com.ratelimit.algorithm.Algorithm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * SpEL expression to compute the rate limit key.
     * Examples: "#userId", "'global'", "#user.id + ':' + #endpoint"
     */
    String key();

    /** Max number of requests allowed in the window. */
    int limit();

    /** Window size. Format: "5s", "1m", "1h", "30s" etc. */
    String window();

    Algorithm algorithm() default Algorithm.TOKEN_BUCKET;
}
