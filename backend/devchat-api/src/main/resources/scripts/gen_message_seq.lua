local prev = redis.call('GET', KEYS[1])
if prev == false then return -1 end
local seq = redis.call('INCR', KEYS[1])
redis.call('EXPIRE', KEYS[1], ARGV[1])
redis.call('ZADD', KEYS[2], ARGV[2], ARGV[3])
redis.call('ZREMRANGEBYRANK', KEYS[2], 0, ARGV[4])
redis.call('SADD', KEYS[3], ARGV[3])
return seq