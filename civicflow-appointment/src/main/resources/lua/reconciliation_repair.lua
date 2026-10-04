-- Result {code,remaining,configVersion}: 0 applied, 1 stale, 2 missing/unsafe.
if redis.call('EXISTS', KEYS[1]) == 0 then return {2, -1, -1} end
local version = tonumber(redis.call('HGET', KEYS[1], 'configVersion') or '-1')
local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining') or '-1')
local seq = tonumber(redis.call('HGET', KEYS[1], 'mutationSeq') or '-1')
local total = tonumber(redis.call('HGET', KEYS[1], 'total') or '-1')
if version ~= tonumber(ARGV[1]) or remaining ~= tonumber(ARGV[2])
    or seq ~= tonumber(ARGV[3]) or total ~= tonumber(ARGV[4]) then
    return {1, remaining, version}
end
if seq < 0 or total < 0 or tonumber(ARGV[5]) < 0 or tonumber(ARGV[5]) > total
    or redis.call('ZCARD', KEYS[2]) ~= 0 then
    return {2, remaining, version}
end
redis.call('HSET', KEYS[1], 'remaining', ARGV[5], 'updatedAtEpochMs', ARGV[6])
redis.call('HINCRBY', KEYS[1], 'mutationSeq', 1)
return {0, tonumber(ARGV[5]), version}
