package com.tencent.wxcloudrun.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册鉴权拦截器并声明白名单，同时开放跨域（便于前端联调）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Autowired
  private JwtInterceptor jwtInterceptor;

  private static final String[] WHITELIST = {
      "/",
      "/index",
      "/error",
      "/static/**",
      "/api/auth/login",
      "/api/auth/register",
      "/api/counter/**"
  };

  @Override
  @SuppressWarnings("all")
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    registry.addInterceptor(jwtInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns(WHITELIST);
  }

  @Override
  public void addCorsMappings(@NonNull CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns("*")
        .allowedMethods("*")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
