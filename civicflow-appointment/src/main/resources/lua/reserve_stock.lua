-- Result: {numericCode, remaining, configVersion}
-- 0 OK, 1 IDEMPOTENT_OK, 10 SLOT_NOT_FOUND, 11 NOT_RELEASED,
-- 12 SLOT_NOT_OPEN, 13 SLOT_CLOSED, 14 CONFIG_VERSION_MISMATCH,
-- 15 DUP_ACTIVE, 16 OUT_OF_STOCK, 17 RESERVATION_CONFLICT, 18 ACTIVE_WRITE_FAILED
if redis.call('EXISTS', KEYS[3]) == 1 then
    if redis.call('HGET', KEYS[3], 'reservationId') == ARGV[1]
        and redis.call('HGET', KEYS[3], 'userId') == ARGV[2]
        and redis.call('HGET', KEYS[3], 'slotId') == ARGV[3] then
        local version = tonumber(redis.call('HGET', KEYS[3], 'configVersion'))
        local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining') or '-1')
        return {1, remaining, version}
    end
    return {17, -1, -1}
end

if redis.call('EXISTS', KEYS[1]) == 0 then
    return {10, -1, -1}
end
local version = tonumber(redis.call('HGET', KEYS[1], 'configVersion'))
if version ~= tonumber(ARGV[8]) then
    return {14, tonumber(redis.call('HGET', KEYS[1], 'remaining')), version}
end
local now = tonumber(ARGV[6])
if now < tonumber(redis.call('HGET', KEYS[1], 'releaseAtEpochMs')) then
    return {11, tonumber(redis.call('HGET', KEYS[1], 'remaining')), version}
end
if now >= tonumber(redis.call('HGET', KEYS[1], 'closeAtEpochMs')) then
    return {13, tonumber(redis.call('HGET', KEYS[1], 'remaining')), version}
end
local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'OPEN' and status ~= 'SCHEDULED' then
    return {12, tonumber(redis.call('HGET', KEYS[1], 'remaining')), version}
end
local active = redis.call('GET', KEYS[2])
if active then
    if active == ARGV[1] then
        return {1, tonumber(redis.call('HGET', KEYS[1], 'remaining')), version}
    end
    return {15, tonumber(redis.call('HGET', KEYS[1], 'remaining')), version}
end
local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining'))
if remaining <= 0 then
    return {16, remaining, version}
end

remaining = redis.call('HINCRBY', KEYS[1], 'remaining', -1)
local activeWritten = redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PXAT', ARGV[10])
if not activeWritten then
    redis.call('HINCRBY', KEYS[1], 'remaining', 1)
    return {18, remaining + 1, version}
end
redis.call('HSET', KEYS[3],
    'reservationId', ARGV[1],
    'userId', ARGV[2],
    'slotId', ARGV[3],
    'itemId', ARGV[4],
    'serviceDate', ARGV[5],
    'state', 'RESERVED',
    'createdAtEpochMs', ARGV[6],
    'publishDeadlineEpochMs', ARGV[7],
    'configVersion', ARGV[8])
redis.call('PEXPIREAT', KEYS[3], ARGV[9])
redis.call('ZADD', KEYS[4], ARGV[7], ARGV[1])
redis.call('PEXPIREAT', KEYS[4], ARGV[10])
redis.call('HINCRBY', KEYS[1], 'mutationSeq', 1)
return {0, remaining, version}
