-- Result: {numericCode, remaining, configVersion}
-- 0 UPDATED, 1 IDEMPOTENT_OK, 20 NEED_RECONCILE, 23 RESERVATION_CONFLICT
if redis.call('EXISTS', KEYS[1]) == 0 then
    return {20, -1, -1}
end
if redis.call('HGET', KEYS[1], 'reservationId') ~= ARGV[1] then
    return {23, -1, -1}
end
local state = redis.call('HGET', KEYS[1], 'state')
local existingAppointmentId = redis.call('HGET', KEYS[1], 'appointmentId')
local remaining = tonumber(redis.call('HGET', KEYS[3], 'remaining') or '-1')
local version = tonumber(redis.call('HGET', KEYS[1], 'configVersion') or '-1')
if state == 'PERSISTED' then
    if existingAppointmentId == ARGV[2] then
        return {1, remaining, version}
    end
    return {23, remaining, version}
end
if state ~= 'RESERVED' and state ~= 'PUBLISHED' then
    return {23, remaining, version}
end
redis.call('HSET', KEYS[1], 'state', 'PERSISTED', 'appointmentId', ARGV[2],
    'persistedAtEpochMs', ARGV[3])
redis.call('PEXPIREAT', KEYS[1], ARGV[4])
redis.call('ZREM', KEYS[2], ARGV[1])
return {0, remaining, version}
