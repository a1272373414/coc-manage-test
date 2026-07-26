package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.AuthUser;
import com.tencent.wxcloudrun.config.JwtUtil;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.dto.LoginRequest;
import com.tencent.wxcloudrun.dto.RegisterRequest;
import com.tencent.wxcloudrun.entity.sys.SysUserRole;
import com.tencent.wxcloudrun.mapper.SysUserRoleMapper;
import com.tencent.wxcloudrun.service.AuthService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	@Resource
	private AuthService authService;

	@Resource
	private JwtUtil jwtUtil;

	@Resource
	private SysUserRoleMapper userRoleMapper;

	@PostMapping("/login")
	public ApiResponse login(@RequestBody LoginRequest req) {
		if (req.getUsername() == null || req.getPassword() == null) {
			return ApiResponse.error("用户名和密码不能为空");
		}
		AuthUser user = authService.login(req.getUsername(), req.getPassword());
		String token = jwtUtil.generateToken(user);
		Map<String, Object> data = new HashMap<>();
		data.put("token", token);
		data.put("user", user);
		return ApiResponse.ok(data);
	}

	@PostMapping("/register")
	public ApiResponse register(@RequestBody RegisterRequest req) {
		authService.register(req);
		return ApiResponse.ok();
	}

	@GetMapping("/info")
	public ApiResponse info() {
		AuthUser current = UserContext.get();
		if (current == null) {
			return ApiResponse.error(401, "未登录");
		}
		return ApiResponse.ok(authService.info(current));
	}

	/**
	 * 分配角色给用户：先删后插，全量替换。 请求体：{ userId, roleIds } - userId 为目标用户 id - roleIds 为新角色 id
	 * 集合，空数组表示清空角色
	 */
	/**
	 * 退出登录。JWT 无状态，服务端无需额外操作，前端只需清空本地 token。
	 */
	@PostMapping("/logout")
	public ApiResponse logout() {
		return ApiResponse.ok();
	}

	@PostMapping("/assignRole")
	@Transactional
	public ApiResponse assignRole(@RequestBody Map<String, Object> body) {
		Object userIdObj = body.get("userId");
		Object roleIdsObj = body.get("roleIds");
		if (userIdObj == null) {
			return ApiResponse.error("userId 不能为空");
		}
		Long userId = ((Number) userIdObj).longValue();
		// 先物理删除该用户的所有角色关联
		userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", userId));
		int count = 0;
		if (roleIdsObj instanceof List) {
			// 用 LinkedHashSet 去重，避免重复插入触发唯一索引冲突
			Set<Long> roleIds = new HashSet<>();
			for (Object o : (List<?>) roleIdsObj) {
				if (o != null)
					roleIds.add(((Number) o).longValue());
			}
			for (Long roleId : roleIds) {
				SysUserRole ur = new SysUserRole();
				ur.setUserId(userId);
				ur.setRoleId(roleId);
				userRoleMapper.insert(ur);
				count++;
			}
		}
		return ApiResponse.ok(Collections.singletonMap("count", count));
	}

	/**
	 * 修改当前登录用户密码。 请求体：{ oldPassword, newPassword }
	 */
	@PostMapping("/changePassword")
	@Transactional
	public ApiResponse changePassword(@RequestBody Map<String, Object> body) {
		AuthUser current = UserContext.get();
		if (current == null) {
			return ApiResponse.error(401, "未登录");
		}
		Object oldObj = body.get("oldPassword");
		Object newObj = body.get("newPassword");
		String oldPassword = oldObj == null ? null : String.valueOf(oldObj);
		String newPassword = newObj == null ? null : String.valueOf(newObj);
		if (oldPassword == null || oldPassword.isEmpty() || newPassword == null || newPassword.isEmpty()) {
			return ApiResponse.error("原密码和新密码均不能为空");
		}
		authService.changePassword(current.getUserId(), oldPassword, newPassword);
		return ApiResponse.ok();
	}

}
