package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
        //从Redis查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //缓存是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //缓存命中直接返回
            return Result.ok(JSONUtil.toBean(shopJson,Shop.class));
        }
        //缓存未命中，查询数据库
        Shop shop = getById(id);
        //数据库存在，写入Redis
        if(shop != null){
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        }
        //数据库没有，错误
        return Result.fail("店铺不存在");
    }
}
