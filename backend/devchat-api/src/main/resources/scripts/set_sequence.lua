local cur = redis.call('GET', KEYS[1])
if cur == false or tonumber(cur) < tonumber(ARGV[1]) then
  redis.call('SET', KEYS[1], ARGV[1])
  redis.call('EXPIRE', KEYS[1], ARGV[2])
end