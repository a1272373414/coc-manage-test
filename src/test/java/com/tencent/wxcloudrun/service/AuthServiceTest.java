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
import com.tencent.wxcloudrun.mapper.ClanGroupMapper;
import com.tencent.wxcloudrun.mapper.SysMenuMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMenuMapper;
import com.tencent.wxcloudrun.mapper.SysUserMapper;
import com.tencent.wxcloudrun.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试：使用 Mockito Mock 所有 Mapper 依赖，
 * 不启动 Spring 上下文，不依赖数据库。
 */
@SuppressWarnings("unchecked")
@DisplayName("认证服务测试")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private SysUserMapper userMapper;
  @Mock
  private SysUserRoleMapper userRoleMapper;
  @Mock
  private ClanGroupMapper clanGroupMapper;
  @Mock
  private SysRoleMapper roleMapper;
  @Mock
  private SysRoleMenuMapper roleMenuMapper;
  @Mock
  private SysMenuMapper menuMapper;

  @InjectMocks
  private AuthService authService;

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  // ==================== login() 测试 ====================

  @Test
  @DisplayName("登录成功 - 普通用户返回正确身份信息")
  void login_success() {
    SysUser user = new SysUser();
    user.setId(1L);
    user.setUsername("admin");
    user.setPassword(encoder.encode("123456"));
    user.setGroupNo("G001");
    user.setStatus(1);

    when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
    when(userRoleMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

    AuthUser result = authService.login("admin", "123456");

    assertEquals(1L, result.getUserId());
    assertEquals("admin", result.getUsername());
    assertEquals("G001", result.getGroupNo());
    assertFalse(result.isSuperAdmin());
  }

  @Test
  @DisplayName("登录失败 - 用户不存在")
  void login_userNotFound() {
    when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> authService.login("nouser", "123456"));
    assertEquals("用户不存在", ex.getMessage());
  }

  @Test
  @DisplayName("登录失败 - 账号已被禁用")
  void login_disabled() {
    SysUser user = new SysUser();
    user.setId(2L);
    user.setUsername("disabled");
    user.setPassword(encoder.encode("123456"));
    user.setStatus(0); // 禁用

    when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> authService.login("disabled", "123456"));
    assertEquals("账号已被禁用", ex.getMessage());
  }

  @Test
  @DisplayName("登录失败 - 密码错误")
  void login_wrongPassword() {
    SysUser user = new SysUser();
    user.setId(3L);
    user.setUsername("admin");
    user.setPassword(encoder.encode("correct-password"));
    user.setStatus(1);

    when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> authService.login("admin", "wrong-password"));
    assertEquals("密码错误", ex.getMessage());
  }

  @Test
  @DisplayName("登录成功 - 超级管理员（group_no 为空）")
  void login_superAdmin() {
    SysUser user = new SysUser();
    user.setId(1L);
    user.setUsername("root");
    user.setPassword(encoder.encode("root123"));
    user.setGroupNo(null); // 超级管理员
    user.setStatus(1);

    when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
    // 超级管理员由 SUPER_ADMIN 角色决定（而非 groupNo 为空）
    SysUserRole sur = new SysUserRole();
    sur.setRoleId(99L);
    SysRole superRole = new SysRole();
    superRole.setId(99L);
    superRole.setRoleCode(RoleConstants.SUPER_ADMIN);
    when(userRoleMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(sur));
    when(roleMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(superRole));

    AuthUser result = authService.login("root", "root123");

    assertTrue(result.isSuperAdmin());
    assertNull(result.getGroupNo());
  }

  // ==================== register() 测试 ====================

  @Test
  @DisplayName("注册成功 - 自动生成 groupNo 并分配角色")
  void register_success() {
    RegisterRequest req = new RegisterRequest();
    req.setUsername("newuser");
    req.setPassword("password123");
    req.setNickname("新用户");

    when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

    // 注册默认分配"游客"角色
    SysRole visitorRole = new SysRole();
    visitorRole.setId(10L);
    visitorRole.setRoleCode(RoleConstants.VISITOR);
    when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(visitorRole);

    SysUser result = authService.register(req);

    assertNotNull(result);
    assertEquals("newuser", result.getUsername());
    assertEquals(1, result.getStatus());
    assertNull(result.getGroupNo()); // 未指定 groupNo 时默认为空

    verify(userMapper, times(1)).insert(any(SysUser.class));
    verify(userRoleMapper, times(1)).insert(any(SysUserRole.class));
    // 注册不再自动创建部落群组
  }

  @Test
  @DisplayName("注册失败 - 用户名为空")
  void register_emptyUsername() {
    RegisterRequest req = new RegisterRequest();
    req.setUsername("");
    req.setPassword("password123");

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> authService.register(req));
    assertEquals("用户名不能为空", ex.getMessage());

    verify(userMapper, never()).insert(any());
  }

  @Test
  @DisplayName("注册失败 - 密码为空")
  void register_emptyPassword() {
    RegisterRequest req = new RegisterRequest();
    req.setUsername("newuser");
    req.setPassword("");

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> authService.register(req));
    assertEquals("密码不能为空", ex.getMessage());

    verify(userMapper, never()).insert(any());
  }

  @Test
  @DisplayName("注册失败 - 用户名已存在")
  void register_usernameExists() {
    RegisterRequest req = new RegisterRequest();
    req.setUsername("existing");
    req.setPassword("password123");

    when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> authService.register(req));
    assertEquals("用户名已存在", ex.getMessage());

    verify(userMapper, never()).insert(any());
  }

  @Test
  @DisplayName("注册成功 - 指定 groupNo 时使用指定值")
  void register_withGroupNo() {
    RegisterRequest req = new RegisterRequest();
    req.setUsername("newuser2");
    req.setPassword("password123");
    req.setGroupNo("CUSTOM001");

    when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
    when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

    SysUser result = authService.register(req);

    assertEquals("CUSTOM001", result.getGroupNo());
  }

  // ==================== toAuthUser() 测试 ====================

  @Test
  @DisplayName("toAuthUser - 正确组装角色与权限")
  void toAuthUser_withRolesAndPermissions() {
    SysUser user = new SysUser();
    user.setId(5L);
    user.setUsername("manager");
    user.setGroupNo("G002");

    SysUserRole userRole = new SysUserRole();
    userRole.setUserId(5L);
    userRole.setRoleId(20L);
    when(userRoleMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.singletonList(userRole));

    SysRole role = new SysRole();
    role.setId(20L);
    role.setRoleCode("GROUP_ADMIN");
    when(roleMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(role));

    SysRoleMenu roleMenu = new SysRoleMenu();
    roleMenu.setRoleId(20L);
    roleMenu.setMenuId(100L);
    when(roleMenuMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.singletonList(roleMenu));

    SysMenu menu = new SysMenu();
    menu.setId(100L);
    menu.setPermission("system:manage");
    when(menuMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(menu));

    AuthUser au = authService.toAuthUser(user);

    assertEquals(5L, au.getUserId());
    assertEquals("manager", au.getUsername());
    assertEquals("G002", au.getGroupNo());
    assertTrue(au.getRoleCodes().contains("GROUP_ADMIN"));
    assertTrue(au.getPermissions().contains("system:manage"));
    assertFalse(au.isSuperAdmin());
  }

  @Test
  @DisplayName("toAuthUser - group_no 为空的用户为超级管理员")
  void toAuthUser_superAdmin() {
    SysUser user = new SysUser();
    user.setId(1L);
    user.setUsername("root");
    user.setGroupNo(null);

    when(userRoleMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.emptyList());

    AuthUser au = authService.toAuthUser(user);

    // group_no 为空但无 SUPER_ADMIN 角色时，不是超级管理员
    assertFalse(au.isSuperAdmin());

    // 超级管理员由 SUPER_ADMIN 角色决定：补充该角色后再断言为 true
    SysUserRole sur = new SysUserRole();
    sur.setRoleId(99L);
    SysRole superRole = new SysRole();
    superRole.setId(99L);
    superRole.setRoleCode(RoleConstants.SUPER_ADMIN);
    when(userRoleMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.singletonList(sur));
    when(roleMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(superRole));

    AuthUser au2 = authService.toAuthUser(user);
    assertTrue(au2.isSuperAdmin());
  }

  // ==================== info() 测试 ====================

  @Test
  @DisplayName("info - 返回用户信息与菜单树")
  void info_returnsUserAndMenus() {
    AuthUser current = new AuthUser();
    current.setUserId(1L);
    current.setUsername("admin");

    SysUser dbUser = new SysUser();
    dbUser.setId(1L);
    dbUser.setUsername("admin");
    dbUser.setGroupNo(null);
    dbUser.setStatus(1);

    when(userMapper.selectById(1L)).thenReturn(dbUser);
    when(userRoleMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.emptyList());

    Map<String, Object> result = authService.info(current);

    assertNotNull(result);
    assertNotNull(result.get("user"));
    assertNotNull(result.get("menus"));
  }

  @Test
  @DisplayName("info - 数据库无此用户时回退到当前上下文")
  void info_userNotFound_fallbackToCurrent() {
    AuthUser current = new AuthUser();
    current.setUserId(999L);
    current.setUsername("ghost");

    when(userMapper.selectById(999L)).thenReturn(null);
    when(userRoleMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.emptyList());

    Map<String, Object> result = authService.info(current);

    AuthUser user = (AuthUser) result.get("user");
    assertEquals("ghost", user.getUsername());
  }

  // ==================== 菜单树排序测试 ====================

  @Test
  @DisplayName("info - 顶级菜单按 sort 字段升序排序")
  void info_menusTopLevelSortedBySort() {
    AuthUser current = new AuthUser();
    current.setUserId(1L);
    current.setUsername("admin");

    SysUser dbUser = new SysUser();
    dbUser.setId(1L);
    dbUser.setUsername("admin");
    dbUser.setGroupNo(null);
    dbUser.setStatus(1);
    when(userMapper.selectById(1L)).thenReturn(dbUser);

    // 用户关联了 GROUP_ADMIN 角色，角色关联了 3 个顶级菜单
    SysUserRole userRole = new SysUserRole();
    userRole.setUserId(1L);
    userRole.setRoleId(20L);

    SysRole role = new SysRole();
    role.setId(20L);
    role.setRoleCode(RoleConstants.GROUP_ADMIN);

    SysRoleMenu roleMenu1 = new SysRoleMenu();
    roleMenu1.setRoleId(20L);
    roleMenu1.setMenuId(101L);
    SysRoleMenu roleMenu2 = new SysRoleMenu();
    roleMenu2.setRoleId(20L);
    roleMenu2.setMenuId(102L);
    SysRoleMenu roleMenu3 = new SysRoleMenu();
    roleMenu3.setRoleId(20L);
    roleMenu3.setMenuId(103L);

    // 3 个顶级菜单故意按"非 sort"顺序返回，模拟数据库返回乱序
    SysMenu mDashboard = new SysMenu();
    mDashboard.setId(101L);
    mDashboard.setParentId(0L);
    mDashboard.setMenuName("数据看板");
    mDashboard.setPath("/dashboard");
    mDashboard.setMenuType(1);
    mDashboard.setSort(50); // sort=50 应排在最前

    SysMenu mSystem = new SysMenu();
    mSystem.setId(102L);
    mSystem.setParentId(0L);
    mSystem.setMenuName("系统管理");
    mSystem.setPath("/system");
    mSystem.setMenuType(1);
    mSystem.setSort(100);

    SysMenu mLeague = new SysMenu();
    mLeague.setId(103L);
    mLeague.setParentId(0L);
    mLeague.setMenuName("联赛管理");
    mLeague.setPath("/league");
    mLeague.setMenuType(1);
    mLeague.setSort(200);

    // userRoleMapper.selectList 在 info/toAuthUser 和 loadUserMenus 各调用一次
    when(userRoleMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.singletonList(userRole));
    when(roleMapper.selectBatchIds(anyCollection()))
        .thenReturn(Collections.singletonList(role));
    when(roleMenuMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Arrays.asList(roleMenu1, roleMenu2, roleMenu3));
    // menuMapper.selectList 调用两次：一次查询祖先补全，一次查询最终列表
    // 第一次（祖先补全）：返回空（无祖先）
    // 第二次（最终列表）：返回 3 个菜单（数据库乱序返回）
    when(menuMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.emptyList())
        .thenReturn(Arrays.asList(mLeague, mSystem, mDashboard)); // 故意反序返回

    Map<String, Object> result = authService.info(current);
    List<MenuNode> menus = (List<MenuNode>) result.get("menus");

    assertNotNull(menus);
    assertEquals(3, menus.size());
    // 验证按 sort 升序排序：dashboard(50) < system(100) < league(200)
    assertEquals("数据看板", menus.get(0).getMenuName());
    assertEquals("系统管理", menus.get(1).getMenuName());
    assertEquals("联赛管理", menus.get(2).getMenuName());
  }

  @Test
  @DisplayName("info - 子菜单按 sort 字段升序排序")
  void info_menusChildrenSortedBySort() {
    AuthUser current = new AuthUser();
    current.setUserId(1L);
    current.setUsername("admin");

    SysUser dbUser = new SysUser();
    dbUser.setId(1L);
    dbUser.setUsername("admin");
    dbUser.setGroupNo(null);
    dbUser.setStatus(1);
    when(userMapper.selectById(1L)).thenReturn(dbUser);

    SysUserRole userRole = new SysUserRole();
    userRole.setUserId(1L);
    userRole.setRoleId(20L);
    SysRole role = new SysRole();
    role.setId(20L);
    role.setRoleCode(RoleConstants.GROUP_ADMIN);

    SysRoleMenu roleMenu1 = new SysRoleMenu();
    roleMenu1.setRoleId(20L);
    roleMenu1.setMenuId(200L);
    SysRoleMenu roleMenu2 = new SysRoleMenu();
    roleMenu2.setRoleId(20L);
    roleMenu2.setMenuId(201L);
    SysRoleMenu roleMenu3 = new SysRoleMenu();
    roleMenu3.setRoleId(20L);
    roleMenu3.setMenuId(202L);

    // 系统管理（父）+ 3 个子菜单，故意子菜单反序返回
    SysMenu pSystem = new SysMenu();
    pSystem.setId(200L);
    pSystem.setParentId(0L);
    pSystem.setMenuName("系统管理");
    pSystem.setPath("/system");
    pSystem.setMenuType(1);
    pSystem.setSort(100);

    SysMenu cClanGroup = new SysMenu();
    cClanGroup.setId(201L);
    cClanGroup.setParentId(200L);
    cClanGroup.setMenuName("部落群组");
    cClanGroup.setPath("/clan/group");
    cClanGroup.setMenuType(1);
    cClanGroup.setSort(10);

    SysMenu cUser = new SysMenu();
    cUser.setId(202L);
    cUser.setParentId(200L);
    cUser.setMenuName("用户管理");
    cUser.setPath("/sys/user");
    cUser.setMenuType(1);
    cUser.setSort(20);

    when(userRoleMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.singletonList(userRole));
    when(roleMapper.selectBatchIds(anyCollection()))
        .thenReturn(Collections.singletonList(role));
    when(roleMenuMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Arrays.asList(roleMenu1, roleMenu2, roleMenu3));
    // 祖先补全：第一次调用返回 system 父菜单（用来确认无更上层的祖先）
    // 第二次调用返回最终菜单列表（按 sys_user/role_join → IN 查询）
    when(menuMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.singletonList(pSystem))
        .thenReturn(Arrays.asList(pSystem, cUser, cClanGroup)); // 故意反序

    Map<String, Object> result = authService.info(current);
    List<MenuNode> menus = (List<MenuNode>) result.get("menus");

    assertEquals(1, menus.size());
    MenuNode systemNode = menus.get(0);
    assertEquals("系统管理", systemNode.getMenuName());

    List<MenuNode> children = systemNode.getChildren();
    assertEquals(2, children.size());
    // 验证子菜单按 sort 升序：clanGroup(10) < user(20)
    assertEquals("部落群组", children.get(0).getMenuName());
    assertEquals("用户管理", children.get(1).getMenuName());
  }

  @Test
  @DisplayName("info - MenuNode 节点携带 sort 字段供前端兜底排序")
  void info_menuNodeCarriesSortField() {
    AuthUser current = new AuthUser();
    current.setUserId(1L);
    current.setUsername("admin");

    SysUser dbUser = new SysUser();
    dbUser.setId(1L);
    dbUser.setUsername("admin");
    dbUser.setGroupNo(null);
    dbUser.setStatus(1);
    when(userMapper.selectById(1L)).thenReturn(dbUser);

    SysUserRole userRole = new SysUserRole();
    userRole.setUserId(1L);
    userRole.setRoleId(20L);
    SysRole role = new SysRole();
    role.setId(20L);
    role.setRoleCode(RoleConstants.GROUP_ADMIN);

    SysRoleMenu rm = new SysRoleMenu();
    rm.setRoleId(20L);
    rm.setMenuId(101L);

    SysMenu m = new SysMenu();
    m.setId(101L);
    m.setParentId(0L);
    m.setMenuName("数据看板");
    m.setPath("/dashboard");
    m.setMenuType(1);
    m.setSort(42);

    when(userRoleMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.singletonList(userRole));
    when(roleMapper.selectBatchIds(anyCollection()))
        .thenReturn(Collections.singletonList(role));
    when(roleMenuMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.singletonList(rm));
    when(menuMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.emptyList())
        .thenReturn(Collections.singletonList(m));

    Map<String, Object> result = authService.info(current);
    List<MenuNode> menus = (List<MenuNode>) result.get("menus");

    assertEquals(1, menus.size());
    MenuNode node = menus.get(0);
    // 验证 sort 字段被正确序列化到 MenuNode（前端可拿到 sort 做兜底排序）
    assertEquals(Integer.valueOf(42), node.getSort());
  }

  @Test
  @DisplayName("info - sort=null 的菜单排在所有指定 sort 菜单之后")
  void info_sortNullMenusAppearLast() {
    AuthUser current = new AuthUser();
    current.setUserId(1L);
    current.setUsername("admin");

    SysUser dbUser = new SysUser();
    dbUser.setId(1L);
    dbUser.setUsername("admin");
    dbUser.setGroupNo(null);
    dbUser.setStatus(1);
    when(userMapper.selectById(1L)).thenReturn(dbUser);

    SysUserRole userRole = new SysUserRole();
    userRole.setUserId(1L);
    userRole.setRoleId(20L);
    SysRole role = new SysRole();
    role.setId(20L);
    role.setRoleCode(RoleConstants.GROUP_ADMIN);

    SysRoleMenu rm1 = new SysRoleMenu();
    rm1.setRoleId(20L);
    rm1.setMenuId(101L);
    SysRoleMenu rm2 = new SysRoleMenu();
    rm2.setRoleId(20L);
    rm2.setMenuId(102L);

    SysMenu mWithSort = new SysMenu();
    mWithSort.setId(101L);
    mWithSort.setParentId(0L);
    mWithSort.setMenuName("有排序");
    mWithSort.setPath("/a");
    mWithSort.setMenuType(1);
    mWithSort.setSort(10);

    SysMenu mNoSort = new SysMenu();
    mNoSort.setId(102L);
    mNoSort.setParentId(0L);
    mNoSort.setMenuName("无排序");
    mNoSort.setPath("/b");
    mNoSort.setMenuType(1);
    mNoSort.setSort(null);

    when(userRoleMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.singletonList(userRole));
    when(roleMapper.selectBatchIds(anyCollection()))
        .thenReturn(Collections.singletonList(role));
    when(roleMenuMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Arrays.asList(rm1, rm2));
    when(menuMapper.selectList(any(QueryWrapper.class)))
        .thenReturn(Collections.emptyList())
        .thenReturn(Arrays.asList(mNoSort, mWithSort)); // 乱序返回

    Map<String, Object> result = authService.info(current);
    List<MenuNode> menus = (List<MenuNode>) result.get("menus");

    assertEquals(2, menus.size());
    // 有 sort 的（10）排在前；sort=null 排在后
    assertEquals("有排序", menus.get(0).getMenuName());
    assertEquals("无排序", menus.get(1).getMenuName());
  }
}
