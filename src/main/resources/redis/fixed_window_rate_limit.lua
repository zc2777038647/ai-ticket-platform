local key = KEYS[1]
local max_requests = tonumber(ARGV[1])
local window_seconds = tonumber(ARGV[2])
local current = redis.call('GET', key)

if not current then
    redis.call('SET', key, 1, 'EX', window_seconds)
    return 0
end

current = tonumber(current)

if current >= max_requests then
    local ttl = redis.call('TTL', key)
    if ttl < 1 then
        redis.call('EXPIRE', key, window_seconds)
        ttl = window_seconds
    end
    return ttl
end

redis.call('INCR', key)
local ttl = redis.call('TTL', key)
if ttl < 1 then
    redis.call('EXPIRE', key, window_seconds)
end

return 0
