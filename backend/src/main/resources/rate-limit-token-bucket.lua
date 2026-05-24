-- KEYS[1] = bucket key (e.g. "blast:rate:<tenantId>")
-- ARGV[1] = capacity
-- ARGV[2] = refill_per_second
-- ARGV[3] = now_ms
-- Returns 0 when a token is granted, otherwise the wait-time-in-ms until one
-- more token will be available.
local capacity = tonumber(ARGV[1])
local refill   = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
local last   = tonumber(redis.call('HGET', KEYS[1], 'last'))

if tokens == nil then tokens = capacity end
if last   == nil then last   = now end

local elapsed_ms = now - last
if elapsed_ms < 0 then elapsed_ms = 0 end
tokens = math.min(capacity, tokens + (elapsed_ms / 1000) * refill)

if tokens >= 1 then
  tokens = tokens - 1
  redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'last', tostring(now))
  redis.call('EXPIRE', KEYS[1], 60)
  return 0
else
  local needed  = 1 - tokens
  local wait_ms = math.ceil(needed * 1000 / refill)
  -- Persist the refill state so the next caller's elapsed_ms math is correct.
  redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'last', tostring(now))
  redis.call('EXPIRE', KEYS[1], 60)
  return wait_ms
end
