-- Result: {code, remaining, configVersion}
-- 0 adjusted, 1 idempotent, 2 stale, 4 version-gap, 5 stock-missing,
-- 6 same-version-conflict, 7 quota-below-consumed
if redis.call('EXISTS', KEYS[1]) == 0 then
    return {5, -1, tonumber(ARGV[6])}
end

local currentVersion = tonumber(redis.call('HGET', KEYS[1], 'configVersion'))
local incomingVersion = tonumber(ARGV[6])
local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining'))
if incomingVersion == currentVersion then
    local same = redis.call('HGET', KEYS[1], 'slotId') == ARGV[1]
        and redis.call('HGET', KEYS[1], 'total') == ARGV[2]
        and redis.call('HGET', KEYS[1], 'status') == ARGV[3]
        and redis.call('HGET', KEYS[1], 'releaseAtEpochMs') == ARGV[4]
        and redis.call('HGET', KEYS[1], 'closeAtEpochMs') == ARGV[5]
    if same then
        return {1, remaining, currentVersion}
    end
    return {6, remaining, currentVersion}
end
if incomingVersion < currentVersion then
    return {2, remaining, currentVersion}
end
if incomingVersion ~= currentVersion + 1 then
    return {4, remaining, currentVersion}
end

local oldTotal = tonumber(redis.call('HGET', KEYS[1], 'total'))
local newRemaining = remaining + tonumber(ARGV[2]) - oldTotal
if newRemaining < 0 then
    return {7, remaining, currentVersion}
end
redis.call('HSET', KEYS[1],
    'total', ARGV[2],
    'remaining', newRemaining,
    'status', ARGV[3],
    'releaseAtEpochMs', ARGV[4],
    'closeAtEpochMs', ARGV[5],
    'configVersion', ARGV[6],
    'updatedAtEpochMs', ARGV[7])
redis.call('HINCRBY', KEYS[1], 'mutationSeq', 1)
redis.call('PEXPIREAT', KEYS[1], ARGV[8])
return {0, newRemaining, incomingVersion}
