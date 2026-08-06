if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end
if redis.call('HGET', KEYS[1], 'state') ~= 'PROCESSING' then
    return -1
end
if redis.call('HGET', KEYS[1], 'fingerprint') ~= ARGV[1] then
    return -2
end
if redis.call('HGET', KEYS[1], 'ownerToken') ~= ARGV[2] then
    return -3
end

redis.call('HSET', KEYS[1], 'state', 'SUCCEEDED', 'response', ARGV[3])
redis.call('HDEL', KEYS[1], 'ownerToken')
redis.call('EXPIRE', KEYS[1], ARGV[4])
return 1
