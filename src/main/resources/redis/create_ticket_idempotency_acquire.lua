local function result(status, retry_after_seconds, response_payload)
    return cjson.encode({
        status = status,
        retryAfterSeconds = retry_after_seconds,
        responsePayload = response_payload or cjson.null
    })
end

if redis.call('EXISTS', KEYS[1]) == 0 then
    redis.call('HSET', KEYS[1],
        'state', 'PROCESSING',
        'fingerprint', ARGV[1],
        'ownerToken', ARGV[2])
    redis.call('EXPIRE', KEYS[1], ARGV[3])
    return result('ACQUIRED', 0, nil)
end

local stored_fingerprint = redis.call('HGET', KEYS[1], 'fingerprint')
if not stored_fingerprint then
    return result('INVALID_RECORD', 0, nil)
end
if stored_fingerprint ~= ARGV[1] then
    return result('PAYLOAD_MISMATCH', 0, nil)
end

local state = redis.call('HGET', KEYS[1], 'state')
if state == 'PROCESSING' then
    local ttl = redis.call('TTL', KEYS[1])
    if ttl < 1 then
        redis.call('EXPIRE', KEYS[1], ARGV[3])
        ttl = tonumber(ARGV[3])
    end
    return result('IN_PROGRESS', ttl, nil)
end

if state == 'SUCCEEDED' then
    local response = redis.call('HGET', KEYS[1], 'response')
    if not response then
        return result('INVALID_RECORD', 0, nil)
    end
    return result('SUCCEEDED', 0, response)
end

return result('INVALID_STATE', 0, nil)
