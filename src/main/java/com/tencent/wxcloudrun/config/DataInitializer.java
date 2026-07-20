package com.tencent.wxcloudrun.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.entity.dict.DictGroup;
import com.tencent.wxcloudrun.entity.dict.DictItem;
import com.tencent.wxcloudrun.entity.sys.SysMenu;
import com.tencent.wxcloudrun.entity.sys.SysRole;
import com.tencent.wxcloudrun.entity.sys.SysRoleMenu;
import com.tencent.wxcloudrun.entity.sys.SysUser;
import com.tencent.wxcloudrun.entity.sys.SysUserRole;
import com.tencent.wxcloudrun.mapper.DictGroupMapper;
import com.tencent.wxcloudrun.mapper.DictItemMapper;
import com.tencent.wxcloudrun.mapper.SysMenuMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMenuMapper;
import com.tencent.wxcloudrun.mapper.SysUserMapper;
import com.tencent.wxcloudrun.mapper.SysUserRoleMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 启动初始化：注入默认角色、超级管理员账号、系统管理菜单以及常用字典数据。
 * 在 UserContext 未设置的启动阶段执行，因此不受 group_no 多租户过滤影响。
 */
@Component
public class DataInitializer implements ApplicationRunner {

  @Resource
  private SysUserMapper userMapper;
  @Resource
  private SysUserRoleMapper userRoleMapper;
  @Resource
  private SysRoleMapper roleMapper;
  @Resource
  private SysMenuMapper menuMapper;
  @Resource
  private SysRoleMenuMapper roleMenuMapper;
  @Resource
  private DictGroupMapper dictGroupMapper;
  @Resource
  private DictItemMapper dictItemMapper;

  private final PasswordEncoder encoder =
      new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

  @Override
  public void run(ApplicationArguments args) {
    Long superAdminRole = ensureRole(RoleConstants.SUPER_ADMIN, "超级管理员");
    Long groupAdminRole = ensureRole(RoleConstants.GROUP_ADMIN, "部落组管理员");
    ensureRole(RoleConstants.LEAGUE_ADMIN, "赛事管理员");
    ensureRole(RoleConstants.MEMBER, "普通成员");

    // 系统管理权限菜单（拦截器据此校验 /api/sys/** 与 /api/dict/**）
    Long sysMenu = ensureMenu("system:manage", "系统管理", "/system", 0);
    assignMenuToRole(superAdminRole, sysMenu);
    assignMenuToRole(groupAdminRole, sysMenu);

    // 顶级导航菜单（无权限标识，登录用户可见，数据按 group_no 隔离）
    Long clanMenu = ensureMenu(null, "部落管理", "/clan", 1);
    Long leagueMenu = ensureMenu(null, "联赛管理", "/league", 1);
    Long warMenu = ensureMenu(null, "部落战管理", "/war", 1);
    Long dashboardMenu = ensureMenu(null, "数据看板", "/dashboard", 1);

    // 部落管理下的二级菜单
    Long clanSubCrudMenu = ensureMenuSub("部落", "/clan/crud", clanMenu, 1);
    Long clanMemberMenu = ensureMenuSub("部落成员", "/clan/member", clanMenu, 2);
    // 部落战管理下的二级菜单
    Long warSubMenu = ensureMenuSub("部落战", "/war/crud", warMenu, 1);
    Long warRecordMenu = ensureMenuSub("部落战战绩", "/war/record", warMenu, 2);
    // 联赛管理下的二级菜单
    Long leagueSubMenu = ensureMenuSub("联赛", "/league/crud", leagueMenu, 1);
    Long leagueRecordMenu = ensureMenuSub("联赛战绩", "/league/record", leagueMenu, 2);
    Long leagueSignupMenu = ensureMenuSub("联赛报名", "/league/signup", leagueMenu, 3);

    // 系统管理下的二级菜单（菜单管理页面可维护这些条目）
    Long clanGroupMenu = ensureMenuSub("部落群组", "/clan/group", sysMenu, 1);
    Long userMenu = ensureMenuSub("用户管理", "/sys/user", sysMenu, 2);
    Long roleMenu = ensureMenuSub("角色管理", "/sys/role", sysMenu, 3);
    Long menuMgmtMenu = ensureMenuSub("菜单管理", "/sys/menu", sysMenu, 4);
    Long dictMenu = ensureMenuSub("字典管理", "/dict", sysMenu, 5);

    // 为超级管理员绑定全部菜单
    Long[] allMenuIds = { sysMenu, clanMenu, leagueMenu, warMenu, dashboardMenu,
        clanSubCrudMenu, clanMemberMenu,
        warSubMenu, warRecordMenu,
        leagueSubMenu, leagueRecordMenu, leagueSignupMenu,
        clanGroupMenu, userMenu, roleMenu, menuMgmtMenu, dictMenu };
    for (Long id : allMenuIds) {
      assignMenuToRole(superAdminRole, id);
    }
    // 部落组管理员：除系统管理外，看不到角色/菜单管理（避免越权）
    Long[] groupAdminMenuIds = { sysMenu, clanGroupMenu, userMenu, dictMenu,
        clanMenu, clanSubCrudMenu, clanMemberMenu,
        warMenu, warSubMenu, warRecordMenu,
        leagueMenu, leagueSubMenu, leagueRecordMenu, leagueSignupMenu };
    for (Long id : groupAdminMenuIds) {
      assignMenuToRole(groupAdminRole, id);
    }

    // 默认超级管理员账号
    if (userMapper.selectCount(new QueryWrapper<>()) == 0) {
      SysUser admin = new SysUser();
      admin.setUsername("admin");
      admin.setPassword(encoder.encode("admin123"));
      admin.setNickname("超级管理员");
      admin.setGroupNo(null);
      admin.setStatus(1);
      userMapper.insert(admin);
      SysUserRole ur = new SysUserRole();
      ur.setUserId(admin.getId());
      ur.setRoleId(superAdminRole);
      userRoleMapper.insert(ur);
    }

    seedDict();

    // 补全顶级菜单的 icon（数据库已有 icon 列，启动时按需回填）
    ensureMenuIcon("数据看板", "Odometer");
    ensureMenuIcon("部落管理", "OfficeBuilding");
    ensureMenuIcon("部落战管理", "DataAnalysis");
    ensureMenuIcon("联赛管理", "Trophy");
    ensureMenuIcon("系统管理", "Setting");
  }

  private Long ensureRole(String code, String name) {
    SysRole role = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", code));
    if (role == null) {
      role = new SysRole();
      role.setRoleCode(code);
      role.setRoleName(name);
      role.setStatus(1);
      roleMapper.insert(role);
    }
    return role.getId();
  }

  private Long ensureMenu(String permission, String name, String path, Integer menuType) {
    SysMenu menu = menuMapper.selectOne(new QueryWrapper<SysMenu>().eq("menu_name", name));
    if (menu == null) {
      menu = new SysMenu();
      menu.setMenuName(name);
      menu.setMenuType(menuType);
      menu.setPath(path);
      menu.setPermission(permission);
      menu.setParentId(0L);
      menu.setSort(0);
      menuMapper.insert(menu);
    }
    return menu.getId();
  }

  /**
   * 创建二级菜单（parentId 指定为父菜单 id）。如果已存在同名菜单则复用。
   * 注意：同名顶级菜单会被复用，因此二级菜单名应与顶级菜单名不同。
   */
  private Long ensureMenuSub(String name, String path, Long parentId, Integer sort) {
    SysMenu menu = menuMapper.selectOne(new QueryWrapper<SysMenu>().eq("menu_name", name));
    if (menu == null) {
      menu = new SysMenu();
      menu.setMenuName(name);
      menu.setMenuType(1);
      menu.setPath(path);
      menu.setParentId(parentId);
      menu.setSort(sort);
      menuMapper.insert(menu);
    } else if (menu.getParentId() == null || menu.getParentId() == 0L) {
      // 兼容旧数据：如果同名菜单是顶级且无父级，升级为子菜单
      menu.setParentId(parentId);
      menu.setSort(sort);
      menuMapper.updateById(menu);
    }
    return menu.getId();
  }

  /**
   * 补全顶级菜单的 icon 字段（数据库已有该列但之前初始化时未设置）。
   * 通过 menuName 匹配已存在的菜单。
   */
  private void ensureMenuIcon(String menuName, String icon) {
    SysMenu menu = menuMapper.selectOne(new QueryWrapper<SysMenu>().eq("menu_name", menuName));
    if (menu != null && (menu.getIcon() == null || menu.getIcon().isEmpty())) {
      menu.setIcon(icon);
      menuMapper.updateById(menu);
    }
  }

  private void assignMenuToRole(Long roleId, Long menuId) {
    if (roleMenuMapper.selectCount(new QueryWrapper<SysRoleMenu>()
        .eq("role_id", roleId).eq("menu_id", menuId)) == 0) {
      SysRoleMenu rm = new SysRoleMenu();
      rm.setRoleId(roleId);
      rm.setMenuId(menuId);
      roleMenuMapper.insert(rm);
    }
  }

  private void seedDict() {
    seedGroup("war_type", "部落战类型", new String[][]{
        {"normal", "普通战"},
        {"league", "联赛"}
    });
    seedGroup("league_type", "联赛类型", new String[][]{
        {"clan_war", "部落战联赛"},
        {"friendly", "友谊赛"}
    });
    seedGroup("member_role", "成员职位", new String[][]{
        {"leader", "首领"},
        {"co_leader", "副首领"},
        {"elder", "长老"},
        {"member", "成员"}
    });
    seedGroup("war_result", "对战结果", new String[][]{
        {"win", "胜利"},
        {"lose", "失败"},
        {"draw", "平局"}
    });
  }

  private void seedGroup(String groupCode, String groupName, String[][] items) {
    DictGroup group = dictGroupMapper.selectOne(new QueryWrapper<DictGroup>().eq("group_code", groupCode));
    if (group == null) {
      group = new DictGroup();
      group.setGroupCode(groupCode);
      group.setGroupName(groupName);
      group.setStatus(1);
      dictGroupMapper.insert(group);
    }
    for (String[] item : items) {
      String itemValue = item[0];
      String itemName = item[1];
      if (dictItemMapper.selectCount(new QueryWrapper<DictItem>()
          .eq("group_code", groupCode).eq("item_value", itemValue)) == 0) {
        DictItem di = new DictItem();
        di.setGroupCode(groupCode);
        di.setItemValue(itemValue);
        di.setItemName(itemName);
        di.setSort(0);
        di.setStatus(1);
        dictItemMapper.insert(di);
      }
    }
  }
}
