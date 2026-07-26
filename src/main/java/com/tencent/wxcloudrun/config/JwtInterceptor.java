package com.tencent.wxcloudrun.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 鉴权拦截器： 1. 校验 JWT 令牌，解析出 AuthUser 存入 UserContext； 2. 规则：超级管理员仅可访问系统基本数据（用户/角色/菜单/配置/字典/认证），
 * 业务数据接口（部落/联赛/部落战/仪表盘等）一律拒绝，即使其角色绑定了相应业务权限； 3. 对所有已登录用户（含超级管理员）按角色绑定权限校验受保护资源。 白名单由
 * WebConfig 控制，未登录访问受保护接口一律拦截。
 */
@Component
@SuppressWarnings("all")
public class JwtInterceptor implements HandlerInterceptor {

	@Autowired
	private JwtUtil jwtUtil;

	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** 需要特定权限的接口路径（ant 风格） -> 所需权限标识 */
	private static final Map<String, String> GUARDED = new LinkedHashMap<>();

	static {
		GUARDED.put("/api/sys/**", RoleConstants.PERM_SYSTEM_MANAGE);
	}

	/**
	 * 系统基本数据接口：仅这些路径允许超级管理员访问。 其余受保护接口（部落 / 联赛 / 部落战 / 仪表盘等业务数据）即使超级管理员角色绑定了相应权限也一律拒绝。
	 */
	private static final List<String> SYSTEM_BASIC_DATA_PATTERNS = Arrays.asList("/api/sys/**", "/api/dict/**",
			"/api/auth/**");

	@Override
	public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull Object handler) throws Exception {
		// 放行 CORS 预检请求
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		String token = resolveToken(request);
		if (token == null) {
			write(response, ApiResponse.error(401, "未登录或缺少令牌"));
			return false;
		}
		AuthUser user;
		try {
			user = jwtUtil.parseToken(token);
		}
		catch (Exception e) {
			write(response, ApiResponse.error(401, "令牌无效或已过期"));
			return false;
		}
		UserContext.set(user);

		// 规则：超级管理员仅可访问系统基本数据，业务数据接口一律拒绝（哪怕其角色绑定了业务权限）
		if (user.isSuperAdmin() && !isSystemBasicData(request.getRequestURI())) {
			write(response, ApiResponse.error(403, "超级管理员仅可访问系统基本数据，无业务数据操作权限"));
			return false;
		}

		String required = matchRequired(request.getRequestURI(), request.getMethod());
		// 所有已登录用户（含超级管理员）均按角色绑定权限校验，不再对超级管理员硬编码放行
		if (required != null && !user.getPermissions().contains(required)) {
			write(response, ApiResponse.error(403, "无访问权限"));
			return false;
		}
		return true;
	}

	@Override
	public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull Object handler, Exception ex) {
		UserContext.clear();
	}

	/** 是否为系统基本数据接口（超级管理员可访问）：用户/角色/菜单/配置/字典/认证 */
	private boolean isSystemBasicData(String path) {
		for (String pattern : SYSTEM_BASIC_DATA_PATTERNS) {
			if (pathMatcher.match(pattern, path)) {
				return true;
			}
		}
		return false;
	}

	private String matchRequired(String path, String method) {
		// 字典读接口（下拉选项 /api/dict/item/page、/api/dict/group 等）对登录用户开放，
		// 业务页面（如联赛段位 league_tier）需要用到这些下拉，不应要求 system:manage。
		if (pathMatcher.match("/api/dict/**", path)) {
			if ("GET".equalsIgnoreCase(method)) {
				return null;
			}
			// 字典写接口按 HTTP 方法要求对应的菜单按钮权限（与前端按钮权限一致）
			if ("POST".equalsIgnoreCase(method))
				return "sys:dict:add";
			if ("PUT".equalsIgnoreCase(method))
				return "sys:dict:edit";
			if ("DELETE".equalsIgnoreCase(method))
				return "sys:dict:delete";
			return null;
		}
		for (Map.Entry<String, String> entry : GUARDED.entrySet()) {
			if (pathMatcher.match(entry.getKey(), path)) {
				return entry.getValue();
			}
		}
		return null;
	}

	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (header != null && header.toLowerCase().startsWith("bearer ")) {
			return header.substring(7).trim();
		}
		String param = request.getParameter("token");
		return (param == null || param.isEmpty()) ? null : param;
	}

	private void write(HttpServletResponse response, ApiResponse apiResponse) throws Exception {
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
	}

}
