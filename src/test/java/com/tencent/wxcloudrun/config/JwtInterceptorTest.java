package com.tencent.wxcloudrun.config;

import com.tencent.wxcloudrun.config.JwtInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * JwtInterceptor 单元测试：聚焦"超级管理员仅可访问系统基本数据，业务数据接口一律拒绝"规则， 以及"所有已登录用户（含超级管理员）均按角色绑定权限校验"。
 * 不启动 Spring 上下文，JwtUtil 以 Mock 注入，请求/响应以 Mockito 模拟。
 */
@SuppressWarnings("null")
@DisplayName("鉴权拦截器测试")
@ExtendWith(MockitoExtension.class)
// 辅助方法统一预设了 request/response 的 mock，部分用例路径（放行/提前 return）不会用到全部 stub，故使用 LENIENT。
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtInterceptorTest {

	@Mock
	private JwtUtil jwtUtil;

	private JwtInterceptor interceptor;

	@BeforeEach
	void setUp() throws Exception {
		interceptor = new JwtInterceptor();
		// 注入 Mock JwtUtil（源码中该字段为 private @Autowired）
		Field field = JwtInterceptor.class.getDeclaredField("jwtUtil");
		field.setAccessible(true);
		field.set(interceptor, jwtUtil);
	}

	@AfterEach
	void tearDown() {
		UserContext.clear();
	}

	// ==================== 辅助方法 ====================

	private AuthUser user(boolean superAdmin, String... perms) {
		AuthUser u = new AuthUser();
		u.setUsername("tester");
		u.setSuperAdmin(superAdmin);
		Set<String> set = new HashSet<>(Arrays.asList(perms));
		u.setPermissions(set);
		return u;
	}

	private HttpServletRequest request(String method, String uri, AuthUser parsedUser) {
		HttpServletRequest r = org.mockito.Mockito.mock(HttpServletRequest.class);
		when(r.getMethod()).thenReturn(method);
		when(r.getRequestURI()).thenReturn(uri);
		when(r.getHeader("Authorization")).thenReturn("Bearer fake-token");
		if (parsedUser != null) {
			when(jwtUtil.parseToken(anyString())).thenReturn(parsedUser);
		}
		return r;
	}

	private HttpServletResponse response(StringWriter sw) throws IOException {
		HttpServletResponse res = org.mockito.Mockito.mock(HttpServletResponse.class);
		when(res.getWriter()).thenReturn(new PrintWriter(sw));
		return res;
	}

	private Object handler() {
		return new Object();
	}

	// ==================== 基础鉴权 ====================

	@Test
	@DisplayName("无令牌 - 返回 401 并拦截")
	void noToken() throws Exception {
		StringWriter sw = new StringWriter();
		HttpServletRequest r = org.mockito.Mockito.mock(HttpServletRequest.class);
		when(r.getMethod()).thenReturn("GET");
		when(r.getRequestURI()).thenReturn("/api/league/list");
		when(r.getHeader("Authorization")).thenReturn(null);
		when(r.getParameter("token")).thenReturn(null);
		boolean ok = interceptor.preHandle(r, response(sw), handler());
		assertFalse(ok);
		assertTrue(sw.toString().contains("未登录"));
	}

	@Test
	@DisplayName("令牌无效 - 返回 401 并拦截")
	void invalidToken() throws Exception {
		StringWriter sw = new StringWriter();
		when(jwtUtil.parseToken(anyString())).thenThrow(new RuntimeException("bad"));
		HttpServletRequest r = request("GET", "/api/league/list", null);
		boolean ok = interceptor.preHandle(r, response(sw), handler());
		assertFalse(ok);
		assertTrue(sw.toString().contains("令牌无效或已过期"));
	}

	@Test
	@DisplayName("CORS 预检请求直接放行")
	void optionsPassThrough() throws Exception {
		HttpServletRequest r = org.mockito.Mockito.mock(HttpServletRequest.class);
		when(r.getMethod()).thenReturn("OPTIONS");
		boolean ok = interceptor.preHandle(r, response(new StringWriter()), handler());
		assertTrue(ok);
	}

	// ==================== 超级管理员规则 ====================

	@Test
	@DisplayName("超级管理员访问业务接口（联赛）被拒绝")
	void superAdmin_blockedFromLeague() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/league/list", user(true)), response(sw), handler());
		assertFalse(ok);
		assertTrue(sw.toString().contains("超级管理员仅可访问系统基本数据"));
	}

	@Test
	@DisplayName("超级管理员访问业务接口（部落）被拒绝")
	void superAdmin_blockedFromClan() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/clan/list", user(true)), response(sw), handler());
		assertFalse(ok);
		assertTrue(sw.toString().contains("超级管理员仅可访问系统基本数据"));
	}

	@Test
	@DisplayName("超级管理员访问仪表盘（业务统计）被拒绝")
	void superAdmin_blockedFromDashboard() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/dashboard", user(true)), response(sw), handler());
		assertFalse(ok);
		assertTrue(sw.toString().contains("超级管理员仅可访问系统基本数据"));
	}

	@Test
	@DisplayName("超级管理员即使绑定业务权限，访问业务接口仍被拒绝")
	void superAdmin_withBusinessPerm_blocked() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/clan/list", user(true, "clan:view")), response(sw),
				handler());
		assertFalse(ok);
		assertTrue(sw.toString().contains("超级管理员仅可访问系统基本数据"));
	}

	@Test
	@DisplayName("超级管理员访问系统接口（/api/sys）且拥有 system:manage 则放行")
	void superAdmin_sysWithPerm_allowed() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/sys/user/list", user(true, "system:manage")),
				response(sw), handler());
		assertTrue(ok);
		assertNotNull(UserContext.get());
	}

	@Test
	@DisplayName("超级管理员访问系统接口（/api/sys）但缺少 system:manage 被拒绝（超管不再硬编码放行）")
	void superAdmin_sysWithoutPerm_blocked() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/sys/user/list", user(true)), response(sw), handler());
		assertFalse(ok);
		assertTrue(sw.toString().contains("无访问权限"));
	}

	@Test
	@DisplayName("超级管理员访问字典接口（/api/dict，系统基本数据）放行")
	void superAdmin_dict_allowed() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/dict/item/page", user(true)), response(sw), handler());
		assertTrue(ok);
		assertNotNull(UserContext.get());
	}

	@Test
	@DisplayName("超级管理员访问认证接口（/api/auth/info）放行")
	void superAdmin_auth_allowed() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/auth/info", user(true)), response(sw), handler());
		assertTrue(ok);
		assertNotNull(UserContext.get());
	}

	// ==================== 普通用户基线 ====================

	@Test
	@DisplayName("普通用户访问系统接口且拥有 system:manage 放行")
	void normalUser_sysWithPerm_allowed() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/sys/user/list", user(false, "system:manage")),
				response(sw), handler());
		assertTrue(ok);
	}

	@Test
	@DisplayName("普通用户访问系统接口缺少 system:manage 被拒绝")
	void normalUser_sysWithoutPerm_blocked() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/sys/user/list", user(false)), response(sw), handler());
		assertFalse(ok);
		assertTrue(sw.toString().contains("无访问权限"));
	}

	@Test
	@DisplayName("普通用户访问业务接口（无权限要求）放行")
	void normalUser_business_allowed() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("GET", "/api/league/list", user(false)), response(sw), handler());
		assertTrue(ok);
	}

	@Test
	@DisplayName("普通用户写字典接口缺少 sys:dict:add 被拒绝")
	void normalUser_dictWriteWithoutPerm_blocked() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("POST", "/api/dict/item", user(false)), response(sw), handler());
		assertFalse(ok);
		assertTrue(sw.toString().contains("无访问权限"));
	}

	@Test
	@DisplayName("普通用户写字典接口拥有 sys:dict:add 放行")
	void normalUser_dictWriteWithPerm_allowed() throws Exception {
		StringWriter sw = new StringWriter();
		boolean ok = interceptor.preHandle(request("POST", "/api/dict/item", user(false, "sys:dict:add")), response(sw),
				handler());
		assertTrue(ok);
	}

}
