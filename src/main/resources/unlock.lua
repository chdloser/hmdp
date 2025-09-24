if(redis.call('get',key[1]) == ARGV[1]) then
    return redis.call('del',keys[1])
end
return 0