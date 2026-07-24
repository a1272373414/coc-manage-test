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

import javax.annotation.Resource;

/**
 * 启动初始化：注入默认角色、超级管理员账号、系统管理菜单以及常用字典数据。
 * 在 UserContext 未设置的启动阶段执行，因此不受 group_no 多租户过滤影响。
 */
// @Component
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
    ensureRole(RoleConstants.VISITOR, "游客");

    // 顶级菜单（全部带 permission 和 sort，按业务优先级排序）
    Long dashboardMenu = ensureMenu("dashboard:view", "数据看板", "/dashboard", 1, 10);
    Long clanMenu = ensureMenu("clan:view", "部落管理", "/clan", 0, 20);
    Long warMenu = ensureMenu("war:view", "部落战管理", "/war", 0, 30);
    Long leagueMenu = ensureMenu("league:view", "联赛管理", "/league", 0, 40);
    Long sysMenu = ensureMenu("system:manage", "系统管理", "/system", 0, 50);
    assignMenuToRole(superAdminRole, sysMenu);
    assignMenuToRole(groupAdminRole, sysMenu);

    // 部落管理下的二级菜单
    Long clanSubCrudMenu = ensureMenuSub("部落", "/clan/crud", clanMenu, 1, "clan:list");
    Long clanMemberMenu = ensureMenuSub("部落成员", "/clan/member", clanMenu, 2, "clan:member:list");
    // 部落战管理下的二级菜单
    Long warSubMenu = ensureMenuSub("部落战", "/war/crud", warMenu, 1, "war:list");
    Long warRecordMenu = ensureMenuSub("部落战战绩", "/war/record", warMenu, 2, "war:record:list");
    // 联赛管理下的二级菜单
    Long leagueSubMenu = ensureMenuSub("联赛", "/league/crud", leagueMenu, 1, "league:list");
    Long leagueScoreMenu = ensureMenuSub("部落成绩", "/league/score", leagueMenu, 2, "league:score:list");
    Long leagueRecordMenu = ensureMenuSub("联赛战绩", "/league/record", leagueMenu, 3, "league:record:list");
    Long leagueSignupMenu = ensureMenuSub("联赛报名", "/league/signup", leagueMenu, 4, "league:signup:list");

    // 系统管理下的二级菜单
    Long clanGroupMenu = ensureMenuSub("部落群组", "/clan/group", sysMenu, 1, "group:list");
    Long userMenu = ensureMenuSub("用户管理", "/sys/user", sysMenu, 2, "sys:user:list");
    Long roleMenu = ensureMenuSub("角色管理", "/sys/role", sysMenu, 3, "sys:role:list");
    Long menuMgmtMenu = ensureMenuSub("菜单管理", "/sys/menu", sysMenu, 4, "sys:menu:list");
    Long dictMenu = ensureMenuSub("字典管理", "/dict", sysMenu, 5, "sys:dict:list");

    // 修复已有历史数据中 permission/sort 为空的菜单（兼容旧版本初始化逻辑产生的数据）
    fixExistingMenus();

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

  private Long ensureMenu(String permission, String name, String path, Integer menuType, Integer sort) {
    SysMenu menu = menuMapper.selectOne(new QueryWrapper<SysMenu>().eq("menu_name", name));
    if (menu == null) {
      menu = new SysMenu();
      menu.setMenuName(name);
      menu.setMenuType(menuType);
      menu.setPath(path);
      menu.setPermission(permission);
      menu.setParentId(0L);
      menu.setSort(sort);
      menuMapper.insert(menu);
    }
    return menu.getId();
  }

  /**
   * 创建二级菜单（parentId 指定为父菜单 id）。如果已存在同名菜单则复用。
   * 注意：同名顶级菜单会被复用，因此二级菜单名应与顶级菜单名不同。
   */
  private Long ensureMenuSub(String name, String path, Long parentId, Integer sort, String permission) {
    SysMenu menu = menuMapper.selectOne(new QueryWrapper<SysMenu>().eq("menu_name", name));
    if (menu == null) {
      menu = new SysMenu();
      menu.setMenuName(name);
      menu.setMenuType(1);
      menu.setPath(path);
      menu.setPermission(permission);
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
   * 修复已有历史数据中 permission 或 sort 为空的菜单。
   * 兼容旧版本 DataInitializer 产生的数据（permission=null, sort=0）。
   * 按菜单名匹配并补全，不影响用户新建的自定义菜单。
   */
  private void fixExistingMenus() {
    // 菜单名 → { permission, sort } 映射表（与 ensureMenu/ensureMenuSub 参数一致）
    java.util.Map<String, String[]> fixMap = new java.util.LinkedHashMap<>();
    fixMap.put("数据看板",   new String[]{"dashboard:view", "10"});
    fixMap.put("部落管理",   new String[]{"clan:view", "20"});
    fixMap.put("部落战管理", new String[]{"war:view", "30"});
    fixMap.put("联赛管理",   new String[]{"league:view", "40"});
    fixMap.put("系统管理",   new String[]{"system:manage", "50"});
    fixMap.put("部落",       new String[]{"clan:list", "1"});
    fixMap.put("部落成员",   new String[]{"clan:member:list", "2"});
    fixMap.put("部落战",     new String[]{"war:list", "1"});
    fixMap.put("部落战战绩", new String[]{"war:record:list", "2"});
    fixMap.put("联赛",       new String[]{"league:list", "1"});
    fixMap.put("部落成绩",   new String[]{"league:score:list", "2"});
    fixMap.put("联赛战绩",   new String[]{"league:record:list", "3"});
    fixMap.put("联赛报名",   new String[]{"league:signup:list", "4"});
    fixMap.put("部落群组",   new String[]{"group:list", "1"});
    fixMap.put("用户管理",   new String[]{"sys:user:list", "2"});
    fixMap.put("角色管理",   new String[]{"sys:role:list", "3"});
    fixMap.put("菜单管理",   new String[]{"sys:menu:list", "4"});
    fixMap.put("字典管理",   new String[]{"sys:dict:list", "5"});

    for (java.util.Map.Entry<String, String[]> entry : fixMap.entrySet()) {
      String menuName = entry.getKey();
      String permission = entry.getValue()[0];
      Integer sort = Integer.parseInt(entry.getValue()[1]);
      SysMenu menu = menuMapper.selectOne(new QueryWrapper<SysMenu>().eq("menu_name", menuName));
      if (menu == null) continue;
      boolean changed = false;
      if (menu.getPermission() == null || menu.getPermission().isEmpty()) {
        menu.setPermission(permission);
        changed = true;
      }
      if (menu.getSort() == null || (menu.getSort() == 0 && sort != 0)) {
        menu.setSort(sort);
        changed = true;
      }
      if (changed) {
        menuMapper.updateById(menu);
      }
    }
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
    // 联赛报名状态
    seedGroup("signup_status", "报名状态", new String[][]{
        {"1", "未报名"},
        {"2", "备选报名"},
        {"3", "主动报名"}
    });
    // 部落冲突联赛段位（6 大段 × 3 小段 = 18 级，value 从低到高 1~18）
    seedGroup("league_tier", "联赛段位", new String[][]{
        {"1",  "铜杯III"},
        {"2",  "铜杯II"},
        {"3",  "铜杯I"},
        {"4",  "银杯III"},
        {"5",  "银杯II"},
        {"6",  "银杯I"},
        {"7",  "金杯III"},
        {"8",  "金杯II"},
        {"9",  "金杯I"},
        {"10", "水晶杯III"},
        {"11", "水晶杯II"},
        {"12", "水晶杯I"},
        {"13", "大师杯III"},
        {"14", "大师杯II"},
        {"15", "大师杯I"},
        {"16", "冠军杯III"},
        {"17", "冠军杯II"},
        {"18", "冠军杯I"}
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
    for (int idx = 0; idx < items.length; idx++) {
      String itemValue = items[idx][0];
      String itemName = items[idx][1];
      if (dictItemMapper.selectCount(new QueryWrapper<DictItem>()
          .eq("group_code", groupCode).eq("item_value", itemValue)) == 0) {
        DictItem di = new DictItem();
        di.setGroupCode(groupCode);
        di.setItemValue(itemValue);
        di.setItemName(itemName);
        di.setSort(idx + 1);
        di.setStatus(1);
        dictItemMapper.insert(di);
      }
    }
  }
}
