package com.tencent.wxcloudrun.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.wxcloudrun.config.AuthUser;
import com.tencent.wxcloudrun.config.JwtUtil;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.dto.LoginRequest;
import com.tencent.wxcloudrun.dto.RegisterRequest;
import com.tencent.wxcloudrun.entity.sys.SysUser;
import com.tencent.wxcloudrun.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController MockMvc 测试（standalone 模式）： - 不启动 Spring 上下文，避免 MyBatis Mapper /
 * DataSource 加载问题 - 使用 MockMvcBuilders.standaloneSetup() 直接挂载 Controller + Mock 依赖 - 聚焦
 * Controller 层的请求参数解析与响应封装逻辑
 */
@SuppressWarnings("null")
@DisplayName("认证接口测试")
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private AuthService authService;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private com.tencent.wxcloudrun.mapper.SysUserRoleMapper userRoleMapper;

	@InjectMocks
	private AuthController authController;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
	}

	@AfterEach
	void tearDown() {
		UserContext.clear();
	}

	// ==================== POST /api/auth/login ====================

	@Test
	@DisplayName("登录成功 - 返回 token 与用户信息")
	void login_success() throws Exception {
		LoginRequest req = new LoginRequest();
		req.setUsername("admin");
		req.setPassword("123456");

		AuthUser mockUser = new AuthUser();
		mockUser.setUserId(1L);
		mockUser.setUsername("admin");

		when(authService.login("admin", "123456")).thenReturn(mockUser);
		when(jwtUtil.generateToken(any(AuthUser.class))).thenReturn("mock-jwt-token");

		mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.token").value("mock-jwt-token"))
			.andExpect(jsonPath("$.data.user.username").value("admin"));
	}

	@Test
	@DisplayName("登录失败 - 用户名或密码为空")
	void login_missingCredentials() throws Exception {
		LoginRequest req = new LoginRequest();
		req.setUsername(null);
		req.setPassword(null);

		mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(400))
			.andExpect(jsonPath("$.errorMsg").value("用户名和密码不能为空"));

		verify(authService, never()).login(any(), any());
	}

	@Test
	@DisplayName("登录失败 - 服务层抛异常（用户不存在）")
	void login_serviceThrows() throws Exception {
		LoginRequest req = new LoginRequest();
		req.setUsername("ghost");
		req.setPassword("123456");

		when(authService.login("ghost", "123456")).thenThrow(new RuntimeException("用户不存在"));

		// standalone MockMvc 模式下未处理异常会以 NestedServletException 向上抛出
		Exception ex = assertThrows(Exception.class,
				() -> mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(req))));
		assertTrue(ex.getCause() != null || ex.getMessage().contains("用户不存在"));
	}

	// ==================== POST /api/auth/register ====================

	@Test
	@DisplayName("注册成功 - 返回 code=0")
	void register_success() throws Exception {
		RegisterRequest req = new RegisterRequest();
		req.setUsername("newuser");
		req.setPassword("password123");

		SysUser createdUser = new SysUser();
		createdUser.setId(1L);
		createdUser.setUsername("newuser");
		when(authService.register(any(RegisterRequest.class))).thenReturn(createdUser);

		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0));
	}

	// ==================== GET /api/auth/info ====================

	@Test
	@DisplayName("获取当前用户信息 - 已登录")
	void info_authenticated() throws Exception {
		AuthUser currentUser = new AuthUser();
		currentUser.setUserId(1L);
		currentUser.setUsername("admin");
		UserContext.set(currentUser);

		Map<String, Object> infoData = new HashMap<>();
		infoData.put("user", currentUser);
		infoData.put("menus", java.util.Collections.emptyList());
		when(authService.info(any(AuthUser.class))).thenReturn(infoData);

		mockMvc.perform(get("/api/auth/info"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.user.username").value("admin"));
	}

	@Test
	@DisplayName("获取当前用户信息 - 未登录返回 401")
	void info_notAuthenticated() throws Exception {
		UserContext.clear();

		mockMvc.perform(get("/api/auth/info"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(401))
			.andExpect(jsonPath("$.errorMsg").value("未登录"));
	}

	// ==================== POST /api/auth/assignRole ====================

	@Test
	@DisplayName("分配角色 - 成功")
	void assignRole_success() throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("userId", 1);
		body.put("roleIds", java.util.Arrays.asList(10, 20));

		mockMvc
			.perform(post("/api/auth/assignRole").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.count").exists());
	}

	@Test
	@DisplayName("分配角色 - 缺少 userId")
	void assignRole_missingUserId() throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("roleIds", java.util.Arrays.asList(10));

		mockMvc
			.perform(post("/api/auth/assignRole").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(400))
			.andExpect(jsonPath("$.errorMsg").value("userId 不能为空"));
	}

}
