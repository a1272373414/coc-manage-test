package com.tencent.wxcloudrun.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserContext 单元测试：验证 ThreadLocal 上下文的设置与清理。
 */
@DisplayName("用户上下文 ThreadLocal 测试")
class UserContextTest {

	@AfterEach
	void tearDown() {
		UserContext.clear();
	}

	@Test
	@DisplayName("设置并获取当前用户")
	void setAndGet() {
		AuthUser user = new AuthUser();
		user.setUserId(1L);
		user.setUsername("admin");
		user.setGroupNo("G001");
		UserContext.set(user);

		assertEquals(1L, UserContext.getUserId());
		assertEquals("G001", UserContext.getGroupNo());
		assertEquals("admin", UserContext.get().getUsername());
	}

	@Test
	@DisplayName("无上下文时返回 null")
	void get_empty() {
		assertNull(UserContext.get());
		assertNull(UserContext.getUserId());
		assertNull(UserContext.getGroupNo());
		assertFalse(UserContext.isSuperAdmin());
	}

	@Test
	@DisplayName("clear() 后上下文为空")
	void clear_afterSet() {
		AuthUser user = new AuthUser();
		user.setUserId(2L);
		UserContext.set(user);

		UserContext.clear();

		assertNull(UserContext.get());
		assertNull(UserContext.getUserId());
	}

	@Test
	@DisplayName("超级管理员标识正确判断")
	void isSuperAdmin_true() {
		AuthUser user = new AuthUser();
		user.setSuperAdmin(true);
		UserContext.set(user);
		assertTrue(UserContext.isSuperAdmin());
	}

	@Test
	@DisplayName("非超级管理员标识正确判断")
	void isSuperAdmin_false() {
		AuthUser user = new AuthUser();
		user.setSuperAdmin(false);
		UserContext.set(user);
		assertFalse(UserContext.isSuperAdmin());
	}

}
