-- Fixed Window algorithm
-- KEYS[1] = base key
-- ARGV[1] = limit
-- ARGV[2] = window size in ms
-- ARGV[3] = now (ms)
--
-- Returns: { allowed (0/1), remaining, retry_after_ms }

local baseKey = KEYS[1]
local limit   = tonumber(ARGV[1])
local window  = tonumber(ARGV[2])
local now     = tonumber(ARGV[3])

local bucket = math.floor(now / window)
local key = baseKey .. ':' .. bucket
local windowEnd = (bucket + 1) * window

local count = redis.call('INCR', key)
if count == 1 then
  redis.call('PEXPIRE', key, window + 1000)
end

if count <= limit then
  return { 1, limit - count, 0 }
end

return { 0, 0, windowEnd - now }
