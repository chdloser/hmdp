package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    private final String LOCK_PREFIX = "lock:";
    private final StringRedisTemplate redisTemplate;
    private final String name;

    public SimpleRedisLock(StringRedisTemplate redisTemplate, String name) {
        this.redisTemplate = redisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, threadId, timeoutSeconds, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        //获取锁的标识
        String id = redisTemplate.opsForValue().get(LOCK_PREFIX + name);
        if(threadId.equals(id)) {
            redisTemplate.delete(LOCK_PREFIX +name);
        }
    }
}
