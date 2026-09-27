-- Result: {numericCode, remaining, configVersion}
-- 0 RELEASED, 1 ALREADY_RELEASED, 20 NEED_RECONCILE,
-- 21 INVARIANT_BROKEN, 22 OCCUPANCY_MISMATCH, 23 RESERVATION_CONFLICT
if redis.call('EXISTS', KEYS[5]) == 1 then
    local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining') or '-1')
    local version = tonumber(redis.call('HGET', KEYS[1], 'configVersion') or '-1')
    return {1, remaining, version}
end
if redis.call('EXISTS', KEYS[3]) == 0 or redis.call('EXISTS', KEYS[1]) == 0 then
    return {20, -1, -1}
end
if redis.call('HGET', KEYS[3], 'reservationId') ~= ARGV[1]
    or redis.call('HGET', KEYS[3], 'userId') ~= ARGV[2]
    or redis.call('HGET', KEYS[3], 'slotId') ~= ARGV[3] then
    return {23, -1, -1}
end
local state = redis.call('HGET', KEYS[3], 'state')
if state ~= 'RESERVED' and state ~= 'PUBLISHED' and state ~= 'PERSISTED'
    and state ~= 'RELEASE_PENDING' then
    return {23, -1, -1}
end
local active = redis.call('GET', KEYS[2])
if active ~= ARGV[1] then
    return {22, tonumber(redis.call('HGET', KEYS[1], 'remaining')),
        tonumber(redis.call('HGET', KEYS[1], 'configVersion'))}
end
local version = tonumber(redis.call('HGET', KEYS[1], 'configVersion'))
if version ~= tonumber(ARGV[6]) then
    return {20, tonumber(redis.call('HGET', KEYS[1], 'remaining')), version}
end
local total = tonumber(redis.call('HGET', KEYS[1], 'total'))
local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining'))
if remaining >= total then
    return {21, remaining, version}
end
remaining = redis.call('HINCRBY', KEYS[1], 'remaining', 1)
redis.call('HINCRBY', KEYS[1], 'mutationSeq', 1)
redis.call('DEL', KEYS[2])
redis.call('DEL', KEYS[3])
redis.call('ZREM', KEYS[4], ARGV[1])
redis.call('SET', KEYS[5], ARGV[4] .. ':' .. ARGV[5], 'PXAT', ARGV[7])
return {0, remaining, version}
