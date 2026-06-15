# Spring Boot Rate Limiter

A distributed rate limiter for Spring Boot apps. Drop the annotation on any method and you get Redis-backed throttling without writing boilerplate every time.

Built this because I kept rewriting the same Lua scripts for different projects that needed rate limiting — figured it was time to package it properly.

## Usage

Add the annotation to any Spring bean method:

```java
@RateLimit(key = "#userId", limit = 100, window = "1m", algorithm = Algorithm.TOKEN_BUCKET)
public Response createOrder(String userId) {
    // your business logic
}
```

That's it. If a user exceeds 100 calls per minute, the request is rejected with HTTP 429.

## Algorithms supported

- **TOKEN_BUCKET** — Allows short bursts. Good for most APIs.
- **SLIDING_WINDOW_LOG** — Most accurate, slightly heavier on memory.
- **FIXED_WINDOW** — Simplest. Has edge-case inaccuracy at window boundaries.

If you don't know which to pick, go with TOKEN_BUCKET. Fixed window has a known edge case at window boundaries so avoid it for strict APIs.

## Running

You need JDK 21, Maven and Docker.

```
docker-compose up -d
mvn spring-boot:run
```

The demo endpoint is at `POST /api/demo/order`. Send the user id as a header:

```
curl -X POST http://localhost:8080/api/demo/order \
  -H "X-User-Id: alice"
```

Hit it more than 5 times in a minute and you'll see `429 Too Many Requests`.

## Tests

```
mvn test
```

Tests use Testcontainers to spin up real Redis — so you need Docker running. There are a few unit tests too that don't need Redis.

The interesting test is `MultiInstanceTest` — it proves that two app instances actually share the rate limit counter (which is the whole point of "distributed").

## Load test

There's a k6 script in `loadtest/`. Install k6 (`brew install k6` on Mac) and run:

```
k6 run loadtest/load.js
```

You'll see ~10% of requests pass and the rest get 429'd. That's the limiter doing its job.

## How it works

Each algorithm has a Lua script in `src/main/resources/scripts/`. The script runs inside Redis atomically, so there are no race conditions when multiple app instances hit the same counter at the same time. That's the whole reason Lua and not just plain GET/SET.

If you're curious, the token bucket script is the easiest to read first.

## Limitations

- Only Redis is supported as a backend. No in-memory fallback right now.
- The annotation key uses SpEL. So you can do `#userId` but not arbitrary expressions involving multiple beans (yet).
- No metrics export. Adding Micrometer is on the todo list.

## License

MIT
