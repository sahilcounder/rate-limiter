-- Sliding Window Log algorithm
-- KEYS[1] = window key (sorted set)
-- ARGV[1] = limit (max requests in window)
-- ARGV[2] = window size in ms
-- ARGV[3] = now (ms)
--
-- Returns: { allowed (0/1), remaining, retry_after_ms }

local key    = KEYS[1]
local limit  = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now    = tonumber(ARGV[3])

-- drop entries older than the window
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

local count = redis.call('ZCARD', key)

if count < limit then
  -- random suffix prevents score collisions on identical timestamps
  redis.call('ZADD', key, now, now .. ':' .. math.random(0, 999999999))
  redis.call('PEXPIRE', key, window + 1000)
  return { 1, limit - count - 1, 0 }
end

local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
local retryAfter = window - (now - tonumber(oldest[2]))
if retryAfter < 0 then retryAfter = 0 end

return { 0, 0, retryAfter }
