package com.ratelimit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class RateLimitApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimitApplication.class, args);
    }
}
