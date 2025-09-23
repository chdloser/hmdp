package com.hmdp.utils;

import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
//请求响应拦截器
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取Session
        HttpSession session = request.getSession();
        //获取Session中的用户
        Object user = session.getAttribute("user");
        //判断用户是否存在
        if (user == null) {
            //不存在则拦截，反正401
            response.setStatus(401);
            return false;
        }
        //用户存在则保持到ThreadLocal
        UserHolder.saveUser((User)user);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

        UserHolder.removeUser();
    }
}
