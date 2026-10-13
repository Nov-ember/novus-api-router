local current = redis.call('GET', KEYS[1])
if current then
    for i = 3, #ARGV do
        if current == ARGV[i] then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            return current
        end
    end
    redis.call('DEL', KEYS[1])
end
redis.call('SET', KEYS[1], ARGV[2], 'NX', 'EX', ARGV[1])
return ARGV[2]