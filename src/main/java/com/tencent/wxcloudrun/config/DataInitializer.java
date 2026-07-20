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
    Long sysMenu = ensureMenu("system:manage", "系统管理", "/system", 1);
    assignMenuToRole(superAdminRole, sysMenu);
    assignMenuToRole(groupAdminRole, sysMenu);

    // 导航菜单（无权限标识，登录用户可见，数据按 group_no 隔离）
    ensureMenu(null, "部落管理", "/clan", 1);
    ensureMenu(null, "联赛管理", "/league", 1);
    ensureMenu(null, "部落战管理", "/war", 1);
    ensureMenu(null, "数据看板", "/dashboard", 1);

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
      menu.setSort(0);
      menuMapper.insert(menu);
    }
    return menu.getId();
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
