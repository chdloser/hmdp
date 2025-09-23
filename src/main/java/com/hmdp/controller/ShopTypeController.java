package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    /**
     * 对ShopTypeList添加缓存
     * 思路和
     * @SEE ShopServiceImpl#queryById 一样
     * 区别在于该结果固定，无需判空
     */
    @GetMapping("list")
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_KEY+"types";
        String value = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(value)){
            List<ShopType> typeList = JSONUtil.toList(value, ShopType.class);
            return Result.ok(typeList);
        }
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
        return Result.ok(typeList);
    }
}
