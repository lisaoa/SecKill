package com.example.seckill.config;

import com.example.seckill.pojo.User;
import com.example.seckill.service.IUserService;
import com.example.seckill.utils.CookieUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.thymeleaf.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 用户参数解析，主要是将url请求中的cookies中的userTicker
 * 封装成controller层中需要的User
 */
@Component
@Slf4j
public class UserArgumentResolver implements HandlerMethodArgumentResolver {
    @Autowired
    IUserService userService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        //只有这个方法返回的结果为TRUE，才会执行下面的resolveArgument方法
        Class<?> clazz = parameter.getParameterType();
        return clazz == User.class;

    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        log.error("进入到了参数解析页面！！！");
        //对参数进行解析
        HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpServletResponse servletResponse = webRequest.getNativeResponse(HttpServletResponse.class);

        //获取userTicket
        String userTicket = CookieUtil.getCookieValue(servletRequest, "userTicket");
        if(StringUtils.isEmpty(userTicket)) return null;

        return userService.getUserByCookie(userTicket,servletRequest,servletResponse);
    }
}
