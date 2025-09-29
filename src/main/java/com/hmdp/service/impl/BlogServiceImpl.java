package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    private final StringRedisTemplate stringRedisTemplate;
    private final IFollowService followService;
    private final StringRedisTemplate redisTemplate;
    @Resource
    private IUserService userService;

    public BlogServiceImpl(IFollowService followService, StringRedisTemplate redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.followService = followService;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
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
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //从当前用户获取key
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FEED_KEY+userId;
        //找到收件箱
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if(tuples==null || tuples.isEmpty()){
            return Result.ok();
        }
        //解析数据，blogId,minTime,offset
        List<Long> ids = new ArrayList<>();
        long minTime = 0L;
        int off = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String idStr = tuple.getValue();
            if (idStr != null) {
                ids.add(Long.valueOf(idStr));
            }
            long time = tuple.getScore().longValue();
            if(time == minTime){
                off++;
            }else{
                minTime = time;
                off = 0;
            }
        }
        //根据id查询blog
        String idStr = StrUtil.join(",",ids);
        List<Blog> blogs = query().in("id",ids).last("order by field(id,"+idStr+")").list();
        ScrollResult result = new ScrollResult();
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        //封装后返回
        result.setList(blogs);
        result.setOffset(offset);
        result.setMinTime(minTime);
        return Result.ok(blogs);

    }

    @Override
    public Result queryBlogById(Long id) {
        //查询Blog
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryBlogUser);
        return Result.ok(records);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
    }
}
