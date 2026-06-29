local key = KEYS[1]
local cooldown_key = KEYS[1] .. ':cooldown'
local now = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])
local cost = tonumber(ARGV[4])
local cooldown_seconds = tonumber(ARGV[5])

-- 쿨다운 중이면 바로 거부
if redis.call('EXISTS', cooldown_key) == 1 then
  return 0
end

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- 첫 요청이면 버킷 초기화
if tokens == nil or last_refill == nil then
  tokens = capacity
  last_refill = now
end

-- 경과 시간만큼 토큰 보충
local elapsed = (now - last_refill) / 1000.0
tokens = math.min(capacity, tokens + elapsed * refill_rate)

-- 토큰 부족 시 쿨다운 설정 후 거부
if tokens < cost then
  redis.call('SET', cooldown_key, 1)
  redis.call('EXPIRE', cooldown_key, cooldown_seconds)
  redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
  redis.call('EXPIRE', key, 60)
  return 0
end

-- 토큰 차감 후 저장
tokens = tokens - cost
redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
redis.call('EXPIRE', key, 60)
return 1