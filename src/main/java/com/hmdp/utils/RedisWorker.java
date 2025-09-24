package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorker {
    private static final long BEGIN =  1640995200L;
    private final StringRedisTemplate redisTemplate;

    public RedisWorker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022,1,1,0,0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }

    /**
     * @param keyPrefix 业务前缀
     * @return 分布式唯一id
     */
    public long nextId(String keyPrefix){
        //生成时间戳（从初始日期开始）
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN;
        //生成序列号
        //获取当前日期，到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长
        @SuppressWarnings("all")
        long count = redisTemplate.opsForValue().increment("irc"+keyPrefix+":"+date);
        //拼接返回

        return timeStamp<<32 | count;
    }
}
