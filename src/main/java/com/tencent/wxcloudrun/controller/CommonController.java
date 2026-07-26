package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.entity.sys.SysRole;
import com.tencent.wxcloudrun.mapper.SysRoleMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 公共基础数据接口：所有已登录用户可读（由 JwtInterceptor 白名单放行）， 不要求 system:manage。
 * 用于业务页面下拉选项等场景（如角色下拉），避免把这些只读基础数据混在 /api/sys 严格权限接口中。
 */
@RestController
@RequestMapping("/api/common")
public class CommonController {

	@Resource
	private SysRoleMapper sysRoleMapper;

	/** 角色下拉选项：返回所有角色的 id 与 roleName，供分配角色等场景使用。所有登录用户可访问。 */
	@GetMapping("/roleOptions")
	public ApiResponse roleOptions() {
		List<SysRole> roles = sysRoleMapper.selectList(new QueryWrapper<SysRole>());
		List<Map<String, Object>> data = roles.stream().map(r -> {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", r.getId());
			m.put("roleName", r.getRoleName());
			return m;
		}).collect(Collectors.toList());
		return ApiResponse.ok(data);
	}
}
