package com.example.seckill.vo;

import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public enum RespBeanEnum {
    //通用
    SUCCESS(200,"SUCCESS"),
    ERROR(500,"服务端异常"),
    //登录模块
    LOGIN_ERROR(500210,"用户名或密码不正确"),
    //MOBILE_ERROR(500211,"手机号码格式不正确"),
    MOBILE_NOT_EXIST(500213,"手机号码不存在"),
    PASSWORD_UPDATE_FAIL(500214,"密码更新失败"),
    USER_NOT_LOGIN(500215,"用户未登录"),
    //参数校验
    BIND_ERROR(500212,"参数校验异常"),
    //秒杀模块
    EMPTY_STOCK(500500,"库存不足"),
    REQUEST_ILLEGAL(500502,"请求地址不合法"),
    ERROR_CAPTCHA(500503,"验证码错误"),
    ACCESS_LIMIT(500504,"访问过于频繁，请稍后重试"),
    REPEAT_ERROR(500501,"不允许重复抢购");


    private final Integer code;
    private final String message;

    RespBeanEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
