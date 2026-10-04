-- Atomic stock and pending snapshot. Missing stock is never rebuilt here.
if redis.call('EXISTS', KEYS[1]) == 0 then
    return {-1, -1, -1, -1, redis.call('ZCARD', KEYS[2])}
end
return {
    tonumber(redis.call('HGET', KEYS[1], 'total') or '-1'),
    tonumber(redis.call('HGET', KEYS[1], 'remaining') or '-1'),
    tonumber(redis.call('HGET', KEYS[1], 'configVersion') or '-1'),
    tonumber(redis.call('HGET', KEYS[1], 'mutationSeq') or '-1'),
    redis.call('ZCARD', KEYS[2])
}
