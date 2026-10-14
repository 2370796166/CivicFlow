-- Authoritative full snapshot; preserve deductions across skipped/stale versions.
-- KEYS: stock, pending. ARGV 9: DB proof that this slot has never had any reservation history.
local incoming = tonumber(ARGV[6])
if redis.call('EXISTS', KEYS[1]) == 0 then
    if redis.call('ZCARD', KEYS[2]) > 0 then return {5, -1, incoming} end
    if tonumber(ARGV[7]) >= tonumber(ARGV[4]) and ARGV[9] ~= 'true' then return {5, -1, incoming} end
    redis.call('HSET', KEYS[1], 'slotId', ARGV[1], 'total', ARGV[2], 'remaining', ARGV[2],
        'status', ARGV[3], 'releaseAtEpochMs', ARGV[4], 'closeAtEpochMs', ARGV[5],
        'configVersion', ARGV[6], 'mutationSeq', 0, 'updatedAtEpochMs', ARGV[7])
    redis.call('PEXPIREAT', KEYS[1], ARGV[8])
    return {0, tonumber(ARGV[2]), incoming}
end
local version = tonumber(redis.call('HGET', KEYS[1], 'configVersion'))
local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining'))
if redis.call('HGET', KEYS[1], 'slotId') ~= ARGV[1] then return {6, remaining, version} end
if incoming < version then return {2, remaining, version} end
if incoming == version then
    if redis.call('HGET', KEYS[1], 'total') ~= ARGV[2] or redis.call('HGET', KEYS[1], 'status') ~= ARGV[3]
        or redis.call('HGET', KEYS[1], 'releaseAtEpochMs') ~= ARGV[4] or redis.call('HGET', KEYS[1], 'closeAtEpochMs') ~= ARGV[5]
        then return {6, remaining, version} end
    return {1, remaining, version}
end
local oldTotal = tonumber(redis.call('HGET', KEYS[1], 'total'))
local nextRemaining = remaining + tonumber(ARGV[2]) - oldTotal
if remaining < 0 or remaining > oldTotal or nextRemaining < 0 then return {7, remaining, version} end
redis.call('HSET', KEYS[1], 'total', ARGV[2], 'remaining', nextRemaining, 'status', ARGV[3],
    'releaseAtEpochMs', ARGV[4], 'closeAtEpochMs', ARGV[5], 'configVersion', ARGV[6], 'updatedAtEpochMs', ARGV[7])
redis.call('HINCRBY', KEYS[1], 'mutationSeq', 1)
redis.call('PEXPIREAT', KEYS[1], ARGV[8])
return {0, nextRemaining, incoming}
