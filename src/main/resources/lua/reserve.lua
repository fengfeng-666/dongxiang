-- All keys use the same Redis hash tag; initialize stock before serving traffic.
local stock = redis.call('GET', KEYS[1])
if not stock then return 3 end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 2 end
if tonumber(stock) <= 0 then return 1 end
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('XADD', KEYS[3], '*', 'orderId', ARGV[2], 'voucherId', ARGV[3], 'userId', ARGV[1])
return 0
