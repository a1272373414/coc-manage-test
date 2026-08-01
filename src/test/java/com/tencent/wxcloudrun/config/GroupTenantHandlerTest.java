package com.tencent.wxcloudrun.config;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GroupTenantHandler 单元测试：聚焦"超级管理员同样按 group_no 隔离业务数据，不再放行全部业务数据"。 直接实例化
 * MyBatisPlusConfig.GroupTenantHandler，通过 UserContext 设置登录上下文。
 */
// @SuppressWarnings("null")
@DisplayName("多租户隔离处理器测试")
class GroupTenantHandlerTest {

	private final MyBatisPlusConfig.GroupTenantHandler handler = new MyBatisPlusConfig.GroupTenantHandler();

	@AfterEach
	void tearDown() {
		UserContext.clear();
	}

	// ==================== 辅助方法 ====================

	private AuthUser superAdmin() {
		AuthUser u = new AuthUser();
		u.setSuperAdmin(true);
		// groupNo 为空（跨部落组）
		return u;
	}

	private AuthUser normal(String groupNo) {
		AuthUser u = new AuthUser();
		u.setSuperAdmin(false);
		u.setGroupNo(groupNo);
		return u;
	}

	// ==================== 超级管理员不再放行业务数据 ====================

	@Test
	@DisplayName("无登录上下文 - 业务表放行（启动/公开/调度场景）")
	void noContext_businessTableIgnored() {
		UserContext.clear();
		assertTrue(handler.ignoreTable("clan_league"));
		assertTrue(handler.ignoreTable("league_record"));
	}

	@Test
	@DisplayName("超级管理员 - 业务表不再放行（按 group_no 隔离）")
	void superAdmin_businessTableNotIgnored() {
		UserContext.set(superAdmin());
		assertFalse(handler.ignoreTable("clan_league"));
		assertFalse(handler.ignoreTable("league_record"));
		assertFalse(handler.ignoreTable("clan_war"));
	}

	@Test
	@DisplayName("超级管理员 - 业务表 tenantId 为空串（实质不可见业务数据）")
	void superAdmin_tenantIdIsEmpty() {
		UserContext.set(superAdmin());
		Expression expr = handler.getTenantId();
		assertTrue(expr instanceof StringValue);
		assertEquals("", ((StringValue) expr).getValue());
	}

	@Test
	@DisplayName("超级管理员 - sys_ 表仍不隔离（系统配置全局共享）")
	void superAdmin_sysTableIgnored() {
		UserContext.set(superAdmin());
		assertTrue(handler.ignoreTable("sys_user"));
		assertTrue(handler.ignoreTable("sys_role"));
		assertTrue(handler.ignoreTable("sys_menu"));
	}

	@Test
	@DisplayName("超级管理员 - dict_ 表仍不隔离（字典全局共享）")
	void superAdmin_dictTableIgnored() {
		UserContext.set(superAdmin());
		assertTrue(handler.ignoreTable("dict_item"));
		assertTrue(handler.ignoreTable("dict_group"));
	}

	@Test
	@DisplayName("超级管理员 - clan_group / clan_group_apply 仍不隔离（群组元数据需全局搜索）")
	void superAdmin_groupTablesIgnored() {
		UserContext.set(superAdmin());
		assertTrue(handler.ignoreTable("clan_group"));
		assertTrue(handler.ignoreTable("clan_group_apply"));
	}

	// ==================== 普通用户基线 ====================

	@Test
	@DisplayName("普通用户 - 业务表按 group_no 隔离")
	void normalUser_businessTableNotIgnored() {
		UserContext.set(normal("G001"));
		assertFalse(handler.ignoreTable("clan_league"));
		Expression expr = handler.getTenantId();
		assertTrue(expr instanceof StringValue);
		assertEquals("G001", ((StringValue) expr).getValue());
	}

	@Test
	@DisplayName("普通用户 - sys_ 表不隔离")
	void normalUser_sysTableIgnored() {
		UserContext.set(normal("G001"));
		assertTrue(handler.ignoreTable("sys_user"));
	}

}
