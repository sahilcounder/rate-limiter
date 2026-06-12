package com.ratelimit.aspect;

import com.ratelimit.annotation.RateLimit;
import com.ratelimit.core.RateLimitResult;
import com.ratelimit.core.RateLimiter;
import com.ratelimit.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimiter rateLimiter;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramDiscoverer = new DefaultParameterNameDiscoverer();
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public RateLimitAspect(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Around("@annotation(rateLimit)")
    public Object enforceLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(joinPoint, rateLimit.key());
        Duration window = parseDuration(rateLimit.window());

        RateLimitResult result = rateLimiter.tryAcquire(key, rateLimit.limit(), window, rateLimit.algorithm());

        if (!result.allowed()) {
            log.debug("Rate limit exceeded for key={}, retryAfter={}ms", key, result.retryAfterMillis());
            throw new RateLimitExceededException(key, rateLimit.limit(), result.retryAfterMillis());
        }

        return joinPoint.proceed();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, String spel) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        String[] paramNames = paramDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext ctx = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                ctx.setVariable(paramNames[i], args[i]);
            }
        }

        Expression expr = expressionCache.computeIfAbsent(spel, parser::parseExpression);
        Object value = expr.getValue(ctx);
        return value == null ? "null" : value.toString();
    }

    // Parse "5s", "1m", "1h" -> Duration
    private Duration parseDuration(String s) {
        s = s.trim().toLowerCase();
        if (s.endsWith("ms")) return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2)));
        if (s.endsWith("s"))  return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length() - 1)));
        if (s.endsWith("m"))  return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1)));
        if (s.endsWith("h"))  return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));
        throw new IllegalArgumentException("Invalid window format: " + s + " (use 5s, 1m, 1h)");
    }
}
