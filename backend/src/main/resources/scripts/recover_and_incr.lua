redis.call('SET', KEYS[1], ARGV[1], 'NX')
local seq = redis.call('INCR', KEYS[1])
redis.call('EXPIRE', KEYS[1], ARGV[2])
redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
redis.call('ZREMRANGEBYRANK', KEYS[2], 0, ARGV[5])
redis.call('SADD', KEYS[3], ARGV[4])
return seq