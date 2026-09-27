-- Remove a terminal appointment's user guard without restoring consumed stock.
-- A newer reservation may already own the key; never remove another value.
if redis.call('GET', KEYS[1]) == ARGV[1] then
    redis.call('DEL', KEYS[1])
    return 1
end
return 0
