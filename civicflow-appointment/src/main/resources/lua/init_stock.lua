-- Result: {code, remaining, configVersion}
-- 0 initialized, 1 idempotent, 2 stale, 3 requires-adjust, 4 version-gap,
-- 5 unsafe-missing-after-release, 6 same-version-conflict
local exists = redis.call('EXISTS', KEYS[1])
local incomingVersion = tonumber(ARGV[6])
if exists == 1 then
    local currentVersion = tonumber(redis.call('HGET', KEYS[1], 'configVersion'))
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
    if incomingVersion == currentVersion + 1 then
        return {3, remaining, currentVersion}
    end
    return {4, remaining, currentVersion}
end

if tonumber(ARGV[7]) >= tonumber(ARGV[4]) then
    return {5, -1, incomingVersion}
end

redis.call('HSET', KEYS[1],
    'slotId', ARGV[1],
    'total', ARGV[2],
    'remaining', ARGV[2],
    'status', ARGV[3],
    'releaseAtEpochMs', ARGV[4],
    'closeAtEpochMs', ARGV[5],
    'configVersion', ARGV[6],
    'mutationSeq', 0,
    'updatedAtEpochMs', ARGV[7])
redis.call('PEXPIREAT', KEYS[1], ARGV[8])
return {0, tonumber(ARGV[2]), incomingVersion}
