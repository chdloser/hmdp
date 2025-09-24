package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private final String name;
    private static final String LOCK_PREFIX = "lock:";
    private static final DefaultRedisScript<Long> unlock_script;

    static {
        unlock_script = new DefaultRedisScript<>();
        unlock_script.setLocation(new ClassPathResource("unlock.lua"));
        unlock_script.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(StringRedisTemplate redisTemplate, String name) {
        this.stringRedisTemplate = redisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, threadId, timeoutSeconds, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //调用lua脚本
        stringRedisTemplate.execute(unlock_script, Collections.singletonList(LOCK_PREFIX+name),threadId);
//        if (threadId.equals(id)) {
//            stringRedisTemplate.delete(LOCK_PREFIX + name);
//        }
    }
}
