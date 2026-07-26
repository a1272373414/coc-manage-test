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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权拦截器： 1. 校验 JWT 令牌，解析出 AuthUser 存入 UserContext； 2. 对受保护资源（系统/字典管理）校验权限标识。 白名单由
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

		String required = matchRequired(request.getRequestURI(), request.getMethod());
		// 超级管理员跳过所有权限校验
		if (required != null && !user.isSuperAdmin() && !user.getPermissions().contains(required)) {
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
