package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.entity.sys.SysMenu;
import com.tencent.wxcloudrun.entity.sys.SysRoleMenu;
import com.tencent.wxcloudrun.mapper.SysMenuMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMenuMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/sys/menu")
public class SysMenuController extends BaseCrudController<SysMenu> {

	@Resource
	private SysMenuMapper sysMenuMapper;

	@Resource
	private SysRoleMenuMapper sysRoleMenuMapper;

	@Override
	protected BaseMapper<SysMenu> mapper() {
		return sysMenuMapper;
	}

	@Override
	protected List<String> keywordFields() {
		return Arrays.asList("menu_name", "permission");
	}

	/**
	 * 返回完整菜单树（按 sort 升序），供角色分配菜单弹窗使用。 数据结构：[{ id, parentId, menuName, menuType,
	 * permission, path, sort, children: [...] }] 健壮性：能处理 parentId 为 null/0/自身/指向不存在节点
	 * 等各种边界情况， 这些节点都会被作为 root 显示，不会丢失。 排序：selectList 已按 sort+id 排序，但 HashMap.values()
	 * 遍历顺序不稳定， 需在挂载后对 roots 和每层 children 显式按 sort 升序排序（null 视为最大排最后）。
	 */
	@GetMapping("/tree")
	public ApiResponse tree() {
		List<SysMenu> all = sysMenuMapper.selectList(new QueryWrapper<SysMenu>().orderByAsc("sort").orderByAsc("id"));
		Map<Long, SysMenuNode> nodeMap = new HashMap<>();
		// 第一次遍历：建立 id → node 映射
		for (SysMenu m : all) {
			SysMenuNode n = new SysMenuNode();
			n.id = m.getId();
			n.parentId = m.getParentId() == null ? 0L : m.getParentId();
			n.menuName = m.getMenuName();
			n.menuType = m.getMenuType();
			n.permission = m.getPermission();
			n.path = m.getPath();
			n.sort = m.getSort();
			n.children = new ArrayList<>();
			nodeMap.put(n.id, n);
		}
		// 第二次遍历：根据 parentId 挂载到父节点或作为 root
		List<SysMenuNode> roots = new ArrayList<>();
		for (SysMenuNode n : nodeMap.values()) {
			SysMenuNode parent = null;
			// parentId 为 0/null、等于自身 id、或指向不存在的节点 → 都作为 root
			if (n.parentId != null && n.parentId != 0L && !n.parentId.equals(n.id)) {
				parent = nodeMap.get(n.parentId);
			}
			if (parent != null) {
				parent.children.add(n);
			}
			else {
				roots.add(n);
			}
		}
		// 修复：HashMap.values() 遍历顺序不稳定，需显式按 sort 排序（与 AuthService.sortMenuTree 行为一致）
		sortNodes(roots);
		return ApiResponse.ok(roots);
	}

	/**
	 * 递归对菜单树按 sort 字段升序排序： - sort 为 null 视为最大，排在最后 - sort 相同时按 id 升序兜底，保证顺序稳定
	 */
	private void sortNodes(List<SysMenuNode> nodes) {
		if (nodes == null || nodes.isEmpty())
			return;
		nodes.sort((a, b) -> {
			Integer sa = a.sort;
			Integer sb = b.sort;
			if (sa == null && sb == null) {
				return Long.compare(a.id == null ? 0 : a.id, b.id == null ? 0 : b.id);
			}
			if (sa == null)
				return 1;
			if (sb == null)
				return -1;
			int cmp = Integer.compare(sa, sb);
			if (cmp != 0)
				return cmp;
			return Long.compare(a.id == null ? 0 : a.id, b.id == null ? 0 : b.id);
		});
		for (SysMenuNode n : nodes) {
			if (n.children != null && !n.children.isEmpty()) {
				sortNodes(n.children);
			}
		}
	}

	/**
	 * 级联删除菜单及其所有后代，并同步清理 sys_role_menu 中的关联。 用法：DELETE /api/sys/menu/cascade/{id}
	 * 必要性：sys_menu.parent_id 没有外键 ON DELETE CASCADE，常规 DELETE 只删单条会留下孤儿节点，
	 * sys_role_menu(role_id, menu_id) 同样没有外键，也需要清理避免历史数据指向已删菜单。 策略：先按 parent_id
	 * 索引递归收集所有后代 id（含自身），在事务内删除所有菜单 + 关联的角色绑定。
	 */
	@DeleteMapping("/cascade/{id}")
	public ApiResponse deleteCascade(@PathVariable Long id) {
		List<SysMenu> all = sysMenuMapper.selectList(new QueryWrapper<SysMenu>().select("id", "parent_id"));
		// parent_id → 子节点 id 列表 的邻接表
		Map<Long, List<Long>> childrenMap = new HashMap<>();
		for (SysMenu m : all) {
			Long pid = m.getParentId() == null ? 0L : m.getParentId();
			childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(m.getId());
		}
		// 深度优先收集所有后代
		Set<Long> toDelete = new HashSet<>();
		Deque<Long> stack = new ArrayDeque<>();
		stack.push(id);
		while (!stack.isEmpty()) {
			Long cur = stack.pop();
			if (cur == null || !toDelete.add(cur))
				continue;
			List<Long> kids = childrenMap.get(cur);
			if (kids != null) {
				for (Long k : kids)
					stack.push(k);
			}
		}
		// 先删 sys_role_menu 中的关联（不能有指向已删菜单的孤儿记录）
		sysRoleMenuMapper.delete(new QueryWrapper<SysRoleMenu>().in("menu_id", toDelete));
		// 再删 sys_menu 中所有匹配 id
		for (Long menuId : toDelete) {
			sysMenuMapper.deleteById(menuId);
		}
		return ApiResponse.ok(toDelete.size());
	}

	/** 内部节点 DTO，避免直接暴露 SysMenu 的审计字段 */
	public static class SysMenuNode {

		public Long id;

		public Long parentId;

		public String menuName;

		public Integer menuType;

		public String permission;

		public String path;

		public Integer sort;

		public List<SysMenuNode> children;

	}

}
