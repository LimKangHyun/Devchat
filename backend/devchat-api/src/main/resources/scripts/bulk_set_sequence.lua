for i = 1, #KEYS do
    local cur = redis.call('GET', KEYS[i])
    if cur == false or tonumber(cur) < tonumber(ARGV[i]) then
        redis.call('SET', KEYS[i], ARGV[i])
        redis.call('EXPIRE', KEYS[i], ARGV[#KEYS + i])
    end
end