package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private final String PREFIX = "lock:";
    private final StringRedisTemplate redisTemplate;
    private final String name;

    public SimpleRedisLock(StringRedisTemplate redisTemplate, String name) {
        this.redisTemplate = redisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        long threadId = Thread.currentThread().getId();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(PREFIX + name, String.valueOf(threadId), timeoutSeconds, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        redisTemplate.delete(PREFIX+name);
    }
}
