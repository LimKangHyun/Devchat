local members = redis.call('SMEMBERS', KEYS[1])
redis.call('DEL', KEYS[1])
return members