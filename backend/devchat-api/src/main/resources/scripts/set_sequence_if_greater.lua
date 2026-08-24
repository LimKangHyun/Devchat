local current = redis.call('GET', KEYS[1])

if current == false or tonumber(ARGV[1]) > tonumber(current) then
    redis.call('SET', KEYS[1], ARGV[1])
    redis.call('EXPIRE', KEYS[1], ARGV[2])
    return 1
end

return 0