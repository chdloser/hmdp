package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 缓存工具类
 * 1.存储对象并设置过期时间
 * 2.存储对象并设置逻辑过期
 * 3.查询缓存，利用控制缓存解决穿透
 * 4.查询缓存，利用逻辑获取解决击穿
 */

@Slf4j
@Component
public class CacheClient {
    final ExecutorService executors = Executors.newFixedThreadPool(5);
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 设置缓存和TTL
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 设置逻辑过期时间
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time));
        RedisData data = new RedisData(expireTime, value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data));
    }

    /**
     * 查询对象，缓存空值处理不存在对象
     * 会返回null
     */
    public <R, ID> R get(String keyPrefix, ID id, Class<R> clazz, Function<ID, R> dbCallBack, long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        //查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //命中且不为空
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, clazz);
        }
        //命中空缓存
        if (json != null) {
            return null;
        }
        //未命中，重建缓存
        //查询数据库
        R r = dbCallBack.apply(id);
        if (r == null) {
            //数据库没有，缓存空值
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            return null;
        }
        this.set(key, r, time, timeUnit);
        return r;
    }

    /**
     * 查询对象，逻辑过期防止击穿问题
     */
    //
    private <R,ID> R queryWithLogicExpire(String keyPrefix,ID id,Class<R> clazz, Function<ID, R> dbCallBack, long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        //直接查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        //命中缓存，判断过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),clazz);
        LocalDateTime expireTime = redisData.getExpireTime();
        //没过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //返回数据
            return r;
        }
        //已过期，重建缓存
        //获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        //判断锁情况
        if (lock) {
            //拿到锁，
            // 再次查询缓存，是否过期
            shopJson = stringRedisTemplate.opsForValue().get(key);
            //判断是否存在
            if (StrUtil.isBlank(shopJson)) {
                return null;
            }
            //命中缓存，判断过期
            redisData = JSONUtil.toBean(shopJson, RedisData.class);
            expireTime = redisData.getExpireTime();
            //没过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                //返回数据
                return r;
            }
            // 开启独立线程重建缓存
            executors.submit(() -> {
                try {
                    R r1 = dbCallBack.apply(id);
                    this.setWithLogicExpire(key,id,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        //未拿到，返回过期的店铺信息
        return r;
    }
    private boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
