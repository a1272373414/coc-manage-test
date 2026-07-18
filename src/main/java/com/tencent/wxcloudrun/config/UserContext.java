package com.tencent.wxcloudrun.config;

/**
 * 登录用户 ThreadLocal 上下文。在请求经由 JwtInterceptor 时写入，请求结束后清除。
 */
public final class UserContext {

  private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

  public static void set(AuthUser user) {
    CURRENT.set(user);
  }

  public static AuthUser get() {
    return CURRENT.get();
  }

  public static Long getUserId() {
    AuthUser user = CURRENT.get();
    return user == null ? null : user.getUserId();
  }

  public static String getGroupNo() {
    AuthUser user = CURRENT.get();
    return user == null ? null : user.getGroupNo();
  }

  public static boolean isSuperAdmin() {
    AuthUser user = CURRENT.get();
    return user != null && user.isSuperAdmin();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
