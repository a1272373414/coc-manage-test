package com.tencent.wxcloudrun.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口免登录：加在 Controller 类或接口方法上，JwtInterceptor 将跳过令牌校验直接放行。
 * 方法上的注解优先于类上的注解；类与方法均无该注解时仍按默认鉴权逻辑处理。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreLogin {
}
