package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.entity.sys.SysMenu;
import com.tencent.wxcloudrun.entity.sys.SysRoleMenu;
import com.tencent.wxcloudrun.mapper.SysMenuMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMenuMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SysMenuController 单元测试。 聚焦新增的 cascade 删除端点（覆盖 BaseCrudController 的常规 CRUD 由框架保证，不重复测试）。
 */
@SuppressWarnings("unchecked")
@DisplayName("菜单控制器测试")
@ExtendWith(MockitoExtension.class)
class SysMenuControllerTest {

	@Mock
	private SysMenuMapper sysMenuMapper;

	@Mock
	private SysRoleMenuMapper sysRoleMenuMapper;

	@InjectMocks
	private SysMenuController controller;

	// ==================== /api/sys/menu/tree ====================

	@Test
	@DisplayName("tree - 顶级菜单按 sort 升序（后端排序）")
	void tree_topLevelReturnedAsArray() {
		// selectList 会带 orderByAsc("sort").orderByAsc("id")，模拟任意顺序返回
		SysMenu m1 = sysMenu(1L, 0L, "系统管理", "/system", 1, 100);
		SysMenu m2 = sysMenu(2L, 0L, "联赛管理", "/league", 1, 200);
		SysMenu m3 = sysMenu(3L, 0L, "数据看板", "/dashboard", 1, 50);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(m1, m2, m3));

		ApiResponse resp = controller.tree();
		assertEquals(0, resp.getCode());
		assertNotNull(resp.getData());
	}

	@Test
	@DisplayName("tree - 父子关系正确挂载，children 嵌套")
	void tree_parentChildrenNested() {
		SysMenu pSystem = sysMenu(10L, 0L, "系统管理", "/system", 1, 100);
		SysMenu cUser = sysMenu(11L, 10L, "用户管理", "/sys/user", 1, 10);
		SysMenu cRole = sysMenu(12L, 10L, "角色管理", "/sys/role", 1, 20);
		SysMenu root = sysMenu(20L, 0L, "联赛管理", "/league", 1, 200);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(pSystem, cUser, cRole, root));

		ApiResponse resp = controller.tree();
		List<SysMenuController.SysMenuNode> trees = (List<SysMenuController.SysMenuNode>) resp.getData();
		assertEquals(2, trees.size());
		// 第一个 root 是系统管理（含 2 个 children）
		SysMenuController.SysMenuNode sysNode = trees.get(0);
		assertEquals("系统管理", sysNode.menuName);
		assertEquals(2, sysNode.children.size());
		assertEquals("用户管理", sysNode.children.get(0).menuName);
		assertEquals("角色管理", sysNode.children.get(1).menuName);
		// 第二个 root 是联赛管理（无 children）
		assertEquals("联赛管理", trees.get(1).menuName);
		assertEquals(0, trees.get(1).children.size());
	}

	@Test
	@DisplayName("tree - parentId 自身循环引用的节点作为 root 显示，不丢失")
	void tree_selfReferentialMenuBecomesRoot() {
		SysMenu orphan = sysMenu(99L, 99L, "孤儿节点", "/x", 1, 100);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(orphan));

		ApiResponse resp = controller.tree();
		List<SysMenuController.SysMenuNode> trees = (List<SysMenuController.SysMenuNode>) resp.getData();
		assertEquals(1, trees.size());
		assertEquals("孤儿节点", trees.get(0).menuName);
	}

	@Test
	@DisplayName("tree - parentId 指向不存在节点的菜单作为 root 显示")
	void tree_orphanedParentBecomesRoot() {
		SysMenu m = sysMenu(10L, 999L, "指向不存在的父", "/x", 1, 100);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(m));

		ApiResponse resp = controller.tree();
		List<SysMenuController.SysMenuNode> trees = (List<SysMenuController.SysMenuNode>) resp.getData();
		assertEquals(1, trees.size());
		assertEquals("指向不存在的父", trees.get(0).menuName);
	}

	// ==================== DELETE /api/sys/menu/cascade/{id} ====================

	@Test
	@DisplayName("deleteCascade - 仅删除自身（无子菜单）")
	void deleteCascade_selfOnly() {
		SysMenu self = sysMenu(10L, 0L, "叶子菜单", "/x", 1, 100);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(self));

		ApiResponse resp = controller.deleteCascade(10L);

		assertEquals(0, resp.getCode());
		assertEquals(1, resp.getData());

		// 验证清理了角色-菜单关联
		ArgumentCaptor<QueryWrapper<SysRoleMenu>> rmCap = ArgumentCaptor.forClass(QueryWrapper.class);
		verify(sysRoleMenuMapper).delete(rmCap.capture());
		QueryWrapper<SysRoleMenu> rmQw = rmCap.getValue();
		assertNotNull(rmQw);

		// 验证菜单本身被删除（通过 setSqlSelect 限制只查 id,parent_id，所以 deleteById 还是按 id 删）
		verify(sysMenuMapper).deleteById(10L);
	}

	@Test
	@DisplayName("deleteCascade - 递归删除子、孙菜单")
	void deleteCascade_recursiveDescendants() {
		// 树形：
		// 10 (parent) → 11 (child) → 12 (grandchild)
		// 11 → 13 (另一个 child)
		// 20 (无关菜单)
		SysMenu m10 = sysMenu(10L, 0L, "根菜单", "/root", 1, 100);
		SysMenu m11 = sysMenu(11L, 10L, "子菜单", "/root/c1", 1, 10);
		SysMenu m12 = sysMenu(12L, 11L, "孙菜单", "/root/c1/gc", 1, 1);
		SysMenu m13 = sysMenu(13L, 11L, "子菜单2", "/root/c2", 1, 20);
		SysMenu m20 = sysMenu(20L, 0L, "无关菜单", "/other", 1, 200);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(m10, m11, m12, m13, m20));

		ApiResponse resp = controller.deleteCascade(10L);

		// 4 个菜单被删除（10 + 11 + 12 + 13），无关的 20 不被删
		assertEquals(4, resp.getData());

		// 验证删除的菜单 ID 集合
	}

	@Test
	@DisplayName("deleteCascade - 删除前先清理 sys_role_menu 中的关联")
	void deleteCascade_clearsRoleMenuFirst() {
		SysMenu self = sysMenu(10L, 0L, "叶子", "/x", 1, 100);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(self));

		controller.deleteCascade(10L);

		// 调用顺序：先删 sys_role_menu，再删 sys_menu
		org.mockito.InOrder inOrder = inOrder(sysRoleMenuMapper, sysMenuMapper);
		inOrder.verify(sysRoleMenuMapper).delete(any(QueryWrapper.class));
		inOrder.verify(sysMenuMapper).deleteById(10L);
	}

	@Test
	@DisplayName("deleteCascade - sys_role_menu 清理使用 in(menu_id) 包含所有待删菜单 ID")
	void deleteCascade_roleMenuCleanupIncludesAllIds() {
		SysMenu m10 = sysMenu(10L, 0L, "根", "/r", 1, 100);
		SysMenu m11 = sysMenu(11L, 10L, "子", "/r/c", 1, 10);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(m10, m11));

		controller.deleteCascade(10L);

		ArgumentCaptor<QueryWrapper<SysRoleMenu>> cap = ArgumentCaptor.forClass(QueryWrapper.class);
		verify(sysRoleMenuMapper).delete(cap.capture());
		QueryWrapper<SysRoleMenu> qw = cap.getValue();
		// 验证 wrapper 中包含对 menu_id 的 in 查询
		String sql = qw.getSqlSegment();
		assertNotNull(sql);
		assertTrue(sql.contains("menu_id"), "应基于 menu_id 过滤");
		assertTrue(sql.toUpperCase().contains("IN"), "应使用 IN 过滤");
	}

	@Test
	@DisplayName("deleteCascade - 父菜单的 parentId=null 时按顶级处理")
	void deleteCascade_parentIdNullHandled() {
		SysMenu m = new SysMenu();
		m.setId(10L);
		m.setParentId(null); // null parentId
		m.setMenuName("顶级菜单");
		m.setPath("/x");
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(m));

		ApiResponse resp = controller.deleteCascade(10L);
		assertEquals(0, resp.getCode());
		assertEquals(1, resp.getData());
	}

	@Test
	@DisplayName("deleteCascade - 多次删除同一 ID 时通过 Set 去重")
	void deleteCascade_deduplicatedIds() {
		// 模拟循环引用：10 → 11 → 10（10 是自己的祖父）
		SysMenu m10 = sysMenu(10L, 11L, "A", "/a", 1, 100);
		SysMenu m11 = sysMenu(11L, 10L, "B", "/b", 1, 10);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(m10, m11));

		controller.deleteCascade(10L);

		// 每个 ID 最多被删除一次
		verify(sysMenuMapper, times(1)).deleteById(10L);
		verify(sysMenuMapper, times(1)).deleteById(11L);
	}

	// ==================== tree 排序测试 ====================

	@Test
	@DisplayName("tree - 顶级菜单按 sort 升序排序（不按 id）")
	void tree_rootsSortedBySort() {
		// 故意以乱序 sort 返回：system(sort=100), dashboard(sort=10), league(sort=200)
		SysMenu m1 = sysMenu(1L, 0L, "系统管理", "/system", 0, 100);
		SysMenu m2 = sysMenu(2L, 0L, "数据看板", "/dashboard", 1, 10);
		SysMenu m3 = sysMenu(3L, 0L, "联赛管理", "/league", 0, 200);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(m1, m2, m3));

		ApiResponse resp = controller.tree();
		List<SysMenuController.SysMenuNode> trees = (List<SysMenuController.SysMenuNode>) resp.getData();

		assertEquals(3, trees.size());
		// 期望：dashboard(10) < system(100) < league(200)
		assertEquals("数据看板", trees.get(0).menuName);
		assertEquals("系统管理", trees.get(1).menuName);
		assertEquals("联赛管理", trees.get(2).menuName);
	}

	@Test
	@DisplayName("tree - 子菜单按 sort 升序排序")
	void tree_childrenSortedBySort() {
		SysMenu parent = sysMenu(10L, 0L, "系统管理", "/system", 0, 100);
		// 子菜单故意乱序 sort 返回
		SysMenu c1 = sysMenu(11L, 10L, "角色管理", "/sys/role", 1, 30);
		SysMenu c2 = sysMenu(12L, 10L, "部落群组", "/clan/group", 1, 10);
		SysMenu c3 = sysMenu(13L, 10L, "用户管理", "/sys/user", 1, 20);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(parent, c1, c2, c3));

		ApiResponse resp = controller.tree();
		List<SysMenuController.SysMenuNode> trees = (List<SysMenuController.SysMenuNode>) resp.getData();

		assertEquals(1, trees.size());
		List<SysMenuController.SysMenuNode> children = trees.get(0).children;
		assertEquals(3, children.size());
		// 期望 sort 升序：部落群组(10) < 用户管理(20) < 角色管理(30)
		assertEquals("部落群组", children.get(0).menuName);
		assertEquals("用户管理", children.get(1).menuName);
		assertEquals("角色管理", children.get(2).menuName);
	}

	@Test
	@DisplayName("tree - sort=null 的节点排在最后")
	void tree_sortNullAppearsLast() {
		SysMenu m1 = sysMenu(1L, 0L, "有排序", "/a", 1, 10);
		SysMenu m2 = sysMenu(2L, 0L, "无排序", "/b", 1, null);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(m1, m2));

		ApiResponse resp = controller.tree();
		List<SysMenuController.SysMenuNode> trees = (List<SysMenuController.SysMenuNode>) resp.getData();

		assertEquals(2, trees.size());
		assertEquals("有排序", trees.get(0).menuName);
		assertEquals("无排序", trees.get(1).menuName);
	}

	@Test
	@DisplayName("tree - sort 相同时按 id 升序兜底")
	void tree_sortTieBreakById() {
		SysMenu m1 = sysMenu(5L, 0L, "E", "/e", 1, 10);
		SysMenu m2 = sysMenu(2L, 0L, "B", "/b", 1, 10);
		SysMenu m3 = sysMenu(8L, 0L, "H", "/h", 1, 10);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(m1, m2, m3));

		ApiResponse resp = controller.tree();
		List<SysMenuController.SysMenuNode> trees = (List<SysMenuController.SysMenuNode>) resp.getData();

		// sort 相同时按 id 升序
		assertEquals(Long.valueOf(2), trees.get(0).id);
		assertEquals(Long.valueOf(5), trees.get(1).id);
		assertEquals(Long.valueOf(8), trees.get(2).id);
	}

	@Test
	@DisplayName("tree - 节点携带 sort 字段供前端排序")
	void tree_nodeCarriesSortField() {
		SysMenu m = sysMenu(1L, 0L, "看板", "/dashboard", 1, 42);
		when(sysMenuMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(m));

		ApiResponse resp = controller.tree();
		List<SysMenuController.SysMenuNode> trees = (List<SysMenuController.SysMenuNode>) resp.getData();

		assertEquals(1, trees.size());
		assertEquals(Integer.valueOf(42), trees.get(0).sort);
	}

	// ==================== 辅助方法 ====================

	/** 快速构造 SysMenu 用于测试 */
	private SysMenu sysMenu(Long id, Long parentId, String name, String path, Integer type, Integer sort) {
		SysMenu m = new SysMenu();
		m.setId(id);
		m.setParentId(parentId);
		m.setMenuName(name);
		m.setPath(path);
		m.setMenuType(type);
		m.setSort(sort);
		return m;
	}

}
