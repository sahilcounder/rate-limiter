-- Token Bucket algorithm
-- KEYS[1] = bucket key
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refill numerator (tokens_per_ms * 1_000_000) -- avoids floats
-- ARGV[3] = now (ms)
-- ARGV[4] = requested tokens
--
-- Returns: { allowed (0/1), remaining_tokens, retry_after_ms }

local key       = KEYS[1]
local capacity  = tonumber(ARGV[1])
local refillNum = tonumber(ARGV[2])  -- tokens per ms * 1e6
local now       = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(data[1])
local lastTs = tonumber(data[2])

if tokens == nil then
  tokens = capacity
  lastTs = now
end

local elapsed = math.max(0, now - lastTs)
local refilled = (elapsed * refillNum) / 1000000
tokens = math.min(capacity, tokens + refilled)

local allowed = 0
local retryAfter = 0

if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  local needed = requested - tokens
  retryAfter = math.ceil(needed * 1000000 / refillNum)
end

redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
redis.call('PEXPIRE', key, 3600000)

return { allowed, math.floor(tokens), retryAfter }
