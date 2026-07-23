package com.tencent.wxcloudrun.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试：验证令牌生成与解析的往返一致性。
 * 不启动 Spring 上下文，通过反射注入 @Value 属性。
 */
@DisplayName("JWT 工具类测试")
class JwtUtilTest {

  private JwtUtil jwtUtil;

  @BeforeEach
  void setUp() throws Exception {
    jwtUtil = new JwtUtil();
    setField(jwtUtil, "secret", "unit-test-secret-key-32-chars-min!!");
    setField(jwtUtil, "expirationMinutes", 60L);
  }

  @Test
  @DisplayName("生成并解析令牌 - 字段一致性")
  void generateAndParseToken_roundTrip() {
    AuthUser user = new AuthUser();
    user.setUserId(1L);
    user.setUsername("admin");
    user.setGroupNo("G12345678");
    user.setSuperAdmin(false);
    user.getRoleCodes().add("GROUP_ADMIN");
    user.getPermissions().add("system:manage");

    String token = jwtUtil.generateToken(user);
    assertNotNull(token);
    assertFalse(token.isEmpty());

    AuthUser parsed = jwtUtil.parseToken(token);
    assertEquals(1L, parsed.getUserId());
    assertEquals("admin", parsed.getUsername());
    assertEquals("G12345678", parsed.getGroupNo());
    assertFalse(parsed.isSuperAdmin());
    assertTrue(parsed.getRoleCodes().contains("GROUP_ADMIN"));
    assertTrue(parsed.getPermissions().contains("system:manage"));
  }

  @Test
  @DisplayName("超级管理员标志正确解析")
  void parseToken_superAdminFlag() {
    AuthUser user = new AuthUser();
    user.setUserId(99L);
    user.setUsername("root");
    user.setSuperAdmin(true);

    String token = jwtUtil.generateToken(user);
    AuthUser parsed = jwtUtil.parseToken(token);
    assertTrue(parsed.isSuperAdmin());
    assertEquals("root", parsed.getUsername());
  }

  @Test
  @DisplayName("篡改的令牌解析抛异常")
  void parseToken_tamperedToken_throws() {
    AuthUser user = new AuthUser();
    user.setUserId(1L);
    user.setUsername("admin");
    String token = jwtUtil.generateToken(user);

    String tampered = token.substring(0, token.length() - 5) + "XXXXX";
    assertThrows(Exception.class, () -> jwtUtil.parseToken(tampered));
  }

  @Test
  @DisplayName("空权限/空角色也能正确往返")
  void generateToken_emptyRolesAndPermissions() {
    AuthUser user = new AuthUser();
    user.setUserId(2L);
    user.setUsername("member");

    String token = jwtUtil.generateToken(user);
    AuthUser parsed = jwtUtil.parseToken(token);
    assertEquals(2L, parsed.getUserId());
    assertTrue(parsed.getRoleCodes().isEmpty());
    assertTrue(parsed.getPermissions().isEmpty());
  }

  /** 通过反射设置 @Value 字段，避免启动 Spring 容器 */
  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
