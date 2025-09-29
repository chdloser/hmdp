package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private final IFollowService followService;
    private final StringRedisTemplate redisTemplate;

    public BlogServiceImpl(IFollowService followService, StringRedisTemplate redisTemplate) {
        this.followService = followService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Result saveBolg(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文笔记
        boolean success = save(blog);
        if (!success){
            return Result.fail("新建笔记失败");
        }
        //查询所有粉丝
        List<Follow> follows = followService.query().eq("follower_id", blog.getUserId()).list();
        //将发布的笔记推送给粉丝
        for (Follow follow : follows) {
            Long id = follow.getId();
            String key = "feed:"+id;
            redisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }
}
