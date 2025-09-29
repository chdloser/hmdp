package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //判断操作是关注/取关
        String key = "follows:"+userId;
        if(isFollow){
            //关注，新增记录
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean success = save(follow);
            //把关注记录放入redis
            if(success){
                stringRedisTemplate.opsForSet().intersect(key, id.toString());
            }
        }else{
            //取关，删除记录
            removeById(new QueryWrapper<Follow>()
            .eq("user_id", userId)
            .eq("follow_user_id", id)
            );
            //从redis中移除记录
            stringRedisTemplate.opsForSet().remove(key, id.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        Integer count = query().eq("follow_user_id", id).eq("user_id", UserHolder.getUser().getId()).count();
        return Result.ok(count==1);
    }

    @Override
    public Result followCommon(Long id) {
        //获取当前用户key
        Long userId = UserHolder.getUser().getId();
        String key = "follows:"+userId;
        //获取目标用户key
        String k2 = "follows:"+userId;
        //求出交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, k2);
        //无交集
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
