package com.tencent.wxcloudrun.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.AuthUser;
import com.tencent.wxcloudrun.config.RoleConstants;
import com.tencent.wxcloudrun.dto.MenuNode;
import com.tencent.wxcloudrun.dto.RegisterRequest;
import com.tencent.wxcloudrun.entity.sys.SysMenu;
import com.tencent.wxcloudrun.entity.sys.SysRole;
import com.tencent.wxcloudrun.entity.sys.SysRoleMenu;
import com.tencent.wxcloudrun.entity.sys.SysUser;
import com.tencent.wxcloudrun.entity.sys.SysUserRole;
import com.tencent.wxcloudrun.mapper.SysMenuMapper;
import com.tencent.wxcloudrun.util.StreamUtils;
import com.tencent.wxcloudrun.mapper.SysRoleMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMenuMapper;
import com.tencent.wxcloudrun.mapper.SysUserMapper;
import com.tencent.wxcloudrun.mapper.SysUserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证与用户信息服务：登录、注册、当前用户信息（含菜单树）。
 */
@Service
@SuppressWarnings("all")
public class AuthService {

	@Resource
	private SysUserMapper userMapper;

	@Resource
	private SysUserRoleMapper userRoleMapper;

	@Resource
	private SysRoleMapper roleMapper;

	@Resource
	private SysRoleMenuMapper roleMenuMapper;

	@Resource
	private SysMenuMapper menuMapper;

	private final PasswordEncoder encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

	public AuthUser login(String username, String password) {
		SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
		if (user == null) {
			throw new RuntimeException("用户不存在");
		}
		if (user.getStatus() != null && user.getStatus() == 0) {
			throw new RuntimeException("账号已被禁用");
		}
		if (!encoder.matches(password, user.getPassword())) {
			throw new RuntimeException("密码错误");
		}
		return toAuthUser(user);
	}

	public SysUser register(RegisterRequest req) {
		if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
			throw new RuntimeException("用户名不能为空");
		}
		if (req.getPassword() == null || req.getPassword().trim().isEmpty()) {
			throw new RuntimeException("密码不能为空");
		}
		if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", req.getUsername())) > 0) {
			throw new RuntimeException("用户名已存在");
		}
		SysUser user = new SysUser();
		user.setUsername(req.getUsername().trim());
		user.setPassword(encoder.encode(req.getPassword()));
		user.setNickname(req.getNickname());
		user.setPhone(req.getPhone());
		// 指定 groupNo 时使用指定值（如 CUSTOM001），否则默认为空（游客）
		user.setGroupNo(req.getGroupNo() != null ? req.getGroupNo().trim() : null);
		user.setStatus(1);
		userMapper.insert(user);

		// 新注册用户默认分配"游客"角色，仅具有基础查看权限
		SysRole visitorRole = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", RoleConstants.VISITOR));
		if (visitorRole != null) {
			SysUserRole ur = new SysUserRole();
			ur.setUserId(user.getId());
			ur.setRoleId(visitorRole.getId());
			userRoleMapper.insert(ur);
		}
		return user;
	}

	public Map<String, Object> info(AuthUser current) {
		SysUser user = userMapper.selectById(current.getUserId());
		Map<String, Object> data = new HashMap<>();
		AuthUser au = user == null ? current : toAuthUser(user);
		// 根据当前用户角色查询其有权访问的菜单（控制左侧导航的显示）
		data.put("user", au);
		data.put("menus", buildMenuTree(loadUserMenus(au), au));
		return data;
	}

	/**
	 * 加载当前用户有权访问的菜单： 所有用户（含超级管理员）都按 sys_user_role → sys_role_menu 关联查询其角色绑定的菜单，
	 * 实现菜单可见性完全由角色菜单绑定表控制。 超级管理员的特殊待遇：通过 init_data.sql 默认为其绑定全部菜单来体现， 而不是在这里硬编码绕过权限过滤。
	 */
	private List<SysMenu> loadUserMenus(AuthUser au) {
		List<SysUserRole> userRoles = userRoleMapper
			.selectList(new QueryWrapper<SysUserRole>().eq("user_id", au.getUserId()));
		if (userRoles.isEmpty()) {
			return new ArrayList<>();
		}
		Set<Long> roleIds = StreamUtils.mapNonNullToSet(userRoles, SysUserRole::getRoleId);
		List<SysRoleMenu> roleMenus = roleMenuMapper.selectList(new QueryWrapper<SysRoleMenu>().in("role_id", roleIds));
		if (roleMenus.isEmpty()) {
			return new ArrayList<>();
		}
		// 角色直接绑定的菜单 id 集合
		Set<Long> menuIds = StreamUtils.mapNonNullToSet(roleMenus, SysRoleMenu::getMenuId);
		// 递归补充所有祖先菜单（parentId=0/null 视为无父级），保证子菜单可见时其父菜单也可见，
		// 避免子菜单"孤儿"在 buildMenuTree 中找不到父节点而被前端过滤掉
		Set<Long> allIds = new HashSet<>(menuIds);
		Set<Long> pending = new HashSet<>(menuIds);
		while (!pending.isEmpty()) {
			List<SysMenu> rows = menuMapper.selectList(new QueryWrapper<SysMenu>().in("id", pending));
			Set<Long> parentIds = new HashSet<>();
			for (SysMenu m : rows) {
				Long pid = m.getParentId();
				if (pid != null && pid > 0)
					parentIds.add(pid);
			}
			pending.clear();
			for (Long pid : parentIds) {
				if (allIds.add(pid))
					pending.add(pid);
			}
		}
		if (allIds.isEmpty()) {
			return new ArrayList<>();
		}
		return menuMapper.selectList(new QueryWrapper<SysMenu>().in("id", allIds).orderByAsc("sort").orderByAsc("id"));
	}

	/** 将 SysUser 转换为携带角色与权限的 AuthUser */
	public AuthUser toAuthUser(SysUser user) {
		AuthUser au = new AuthUser();
		au.setUserId(user.getId());
		au.setUsername(user.getUsername());
		au.setGroupNo(user.getGroupNo());
		// superAdmin 完全由角色表中的 SUPER_ADMIN 角色决定，不依赖 groupNo 是否为空

		List<SysUserRole> userRoles = userRoleMapper
			.selectList(new QueryWrapper<SysUserRole>().eq("user_id", user.getId()));
		Set<Long> roleIds = StreamUtils.mapNonNullToSet(userRoles, SysUserRole::getRoleId);

		if (!roleIds.isEmpty()) {
			List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
			for (SysRole role : roles) {
				au.getRoleCodes().add(role.getRoleCode());
				if (RoleConstants.SUPER_ADMIN.equals(role.getRoleCode())) {
					au.setSuperAdmin(true);
				}
			}
			List<SysRoleMenu> roleMenus = roleMenuMapper
				.selectList(new QueryWrapper<SysRoleMenu>().in("role_id", roleIds));
			Set<Long> menuIds = StreamUtils.mapNonNullToSet(roleMenus, SysRoleMenu::getMenuId);
			if (!menuIds.isEmpty()) {
				List<SysMenu> menus = menuMapper.selectBatchIds(menuIds);
				for (SysMenu menu : menus) {
					if (menu.getPermission() != null && !menu.getPermission().isEmpty()) {
						au.getPermissions().add(menu.getPermission());
					}
				}
			}
		}
		return au;
	}

	private List<MenuNode> buildMenuTree(List<SysMenu> all, AuthUser current) {
		// 输入已按用户角色过滤（loadUserMenus），此处仅排除按钮类型（menuType=2）
		List<SysMenu> filtered = all.stream()
			.filter(m -> m.getMenuType() == null || m.getMenuType() != 2)
			.collect(Collectors.toList());
		Map<Long, MenuNode> nodeMap = new HashMap<>();
		for (SysMenu m : filtered) {
			MenuNode node = new MenuNode();
			node.setId(m.getId());
			node.setParentId(m.getParentId());
			node.setMenuName(m.getMenuName());
			node.setMenuType(m.getMenuType());
			node.setPath(m.getPath());
			node.setComponent(m.getComponent());
			node.setIcon(m.getIcon());
			node.setPermission(m.getPermission());
			node.setSort(m.getSort());
			node.setChildren(new ArrayList<>());
			nodeMap.put(m.getId(), node);
		}
		List<MenuNode> roots = new ArrayList<>();
		for (MenuNode node : nodeMap.values()) {
			MenuNode parent = null;
			Long pid = node.getParentId();
			// parentId 为 null/0/等于自身 id/指向不存在节点 → 都作为 root
			if (pid != null && pid != 0L && !pid.equals(node.getId())) {
				parent = nodeMap.get(pid);
			}
			if (parent == null) {
				roots.add(node);
			}
			else {
				parent.getChildren().add(node);
			}
		}
		// 修复：原代码按 id 排序忽略 sort 字段；改为按 sort 升序（null 排最后），相同时按 id 兜底
		sortMenuTree(roots);
		return roots;
	}

	/**
	 * 递归对菜单树按 sort 字段升序排序： - sort 为 null 视为最大，排在最后 - sort 相同时按 id 升序兜底，保证顺序稳定 -
	 * 同步递归处理每个节点的 children
	 */
	private void sortMenuTree(List<MenuNode> nodes) {
		if (nodes == null || nodes.isEmpty())
			return;
		nodes.sort((a, b) -> {
			Integer sa = a.getSort();
			Integer sb = b.getSort();
			if (sa == null && sb == null) {
				return Long.compare(a.getId() == null ? 0 : a.getId(), b.getId() == null ? 0 : b.getId());
			}
			if (sa == null)
				return 1;
			if (sb == null)
				return -1;
			int cmp = Integer.compare(sa, sb);
			if (cmp != 0)
				return cmp;
			return Long.compare(a.getId() == null ? 0 : a.getId(), b.getId() == null ? 0 : b.getId());
		});
		for (MenuNode node : nodes) {
			if (node.getChildren() != null && !node.getChildren().isEmpty()) {
				sortMenuTree(node.getChildren());
			}
		}
	}

	/**
	 * 修改用户密码：校验原密码正确后，使用 BCrypt 加密新密码并更新。
	 */
	public void changePassword(Long userId, String oldPassword, String newPassword) {
		SysUser user = userMapper.selectById(userId);
		if (user == null) {
			throw new RuntimeException("用户不存在");
		}
		if (oldPassword == null || oldPassword.isEmpty()) {
			throw new RuntimeException("原密码不能为空");
		}
		if (newPassword == null || newPassword.isEmpty()) {
			throw new RuntimeException("新密码不能为空");
		}
		if (!encoder.matches(oldPassword, user.getPassword())) {
			throw new RuntimeException("原密码错误");
		}
		user.setPassword(encoder.encode(newPassword));
		userMapper.updateById(user);
	}

}
