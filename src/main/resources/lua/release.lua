-- The order marker makes repeated outbox processing safe. Retain buyer membership:
-- the database unique constraint enforces one order per user for the entire voucher.
if not redis.call('GET', KEYS[1]) then return 0 end
if redis.call('SADD', KEYS[2], ARGV[1]) == 1 then redis.call('INCR', KEYS[1]) end
return 1
