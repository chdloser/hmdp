package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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

    @Override
    public Result follow(Long id, Boolean isFollow) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //判断操作是关注/取关
        if(isFollow){
            //关注，新增记录
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            save(follow);
        }else{
            //取关，删除记录
            removeById(new QueryWrapper<Follow>()
            .eq("user_id", userId)
            .eq("follow_user_id", id)
            );
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        Integer count = query().eq("follow_user_id", id).eq("user_id", UserHolder.getUser().getId()).count();
        return Result.ok(count==1);
    }
}
