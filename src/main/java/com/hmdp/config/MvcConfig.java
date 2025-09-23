package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    /**
     * 注册添加拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //token拦截器会拦截所有请求
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate));//设置优先，因为刷新token时会查询用户存ThreadLocal，先登录校验ThreadLocal是空的的，会被拦截。
        //登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                //排除不需要拦截的目录
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);
       }
}
