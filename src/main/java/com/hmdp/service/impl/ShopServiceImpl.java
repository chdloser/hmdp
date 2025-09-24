package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //从Redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //缓存是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //缓存命中直接返回
            return Result.ok(JSONUtil.toBean(shopJson,Shop.class));
        }
        //缓存穿透问题：判断命中是否为空字符串（空值缓存）
        if("".equals(shopJson)){
            return Result.fail("店铺不存在");
        }
        //缓存未命中，重建缓存
        //获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        try {
            boolean lock = tryLock(lockKey);
            //判断锁情况
            if(!lock){
                //获取锁失败，休眠并重试
                try {
                    Thread.sleep(100);
                    //重试-> 递归
                    return queryById(id);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            //获取锁成功，查询数据，写入Redis
            Shop shop = getById(id);
            Thread.sleep(500); //模拟重建缓存耗时
            //数据库存在，写入Redis
            if(shop != null){
                stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return Result.ok(shop);
            }
            //数据库没有，缓存空值后返回错误
            stringRedisTemplate.opsForValue().set(key,"",2, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        } catch (RuntimeException e) {
            System.err.println("异常："+e);
            throw e;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unlock(lockKey);
        }
    }
    final ExecutorService executors = Executors.newFixedThreadPool(5);
    /**
     * 缓存预热：将指定数据写入Redis，并使用逻辑过期时间
     * @param id 商品Id
     * @param expireSeconds 过期时间/秒
     */
    private void save2Redis(Long id,Long expireSeconds){
        RedisData redisData = new RedisData();
        Shop shop = getById(id);
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 防止击穿问题的查询
     */
    //
    private Shop queryByIdWithLogicExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        //直接查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        //命中缓存，判断过期
        RedisData redisData = JSONUtil.toBean(shopJson,RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //没过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //返回数据
            return (Shop) redisData.getData();
        }
        //已过期，重建缓存
        //获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        boolean lock = tryLock(lockKey);
        //判断锁情况
        if(lock){
            //拿到锁，
            // 再次查询缓存，是否过期
            shopJson = stringRedisTemplate.opsForValue().get(key);
            //判断是否存在
            if(StrUtil.isBlank(shopJson)){
                return null;
            }
            //命中缓存，判断过期
            redisData = JSONUtil.toBean(shopJson,RedisData.class);
            expireTime = redisData.getExpireTime();
            //没过期
            if(expireTime.isAfter(LocalDateTime.now())){
                //返回数据
                return (Shop) redisData.getData();
            }
            // 开启独立线程重建缓存
            executors.submit(()->{
                this.save2Redis(id,30L*60);
                unlock(lockKey);
            });
        }
        //未拿到，返回过期的店铺信息
        return (Shop) redisData.getData();
    }
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("id不存在");
        }
        //先更新数据库
        updateById(shop);
        //再更新缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
    private boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
