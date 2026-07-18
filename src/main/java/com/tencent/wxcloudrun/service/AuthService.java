package com.tencent.wxcloudrun.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.AuthUser;
import com.tencent.wxcloudrun.config.RoleConstants;
import com.tencent.wxcloudrun.dto.MenuNode;
import com.tencent.wxcloudrun.dto.RegisterRequest;
import com.tencent.wxcloudrun.entity.biz.ClanGroup;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 认证与用户信息服务：登录、注册、当前用户信息（含菜单树）。
 */
@Service
public class AuthService {

  @Resource
  private SysUserMapper userMapper;
  @Resource
  private SysUserRoleMapper userRoleMapper;
  @Resource
  private ClanGroupMapper clanGroupMapper;
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

    String groupNo = req.getGroupNo();
    if (groupNo == null || groupNo.trim().isEmpty()) {
      groupNo = generateGroupNo();
    }
    user.setGroupNo(groupNo);
    user.setStatus(1);
    userMapper.insert(user);

    // 自助注册默认分配“部族组管理员”角色，可管理本组部落与成员
    SysRole groupAdmin = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", RoleConstants.GROUP_ADMIN));
    if (groupAdmin != null) {
      SysUserRole ur = new SysUserRole();
      ur.setUserId(user.getId());
      ur.setRoleId(groupAdmin.getId());
      userRoleMapper.insert(ur);
    }
    // 同步创建部族群组记录，便于部族/成员管理
    ClanGroup group = new ClanGroup();
    group.setGroupNo(groupNo);
    group.setGroupName((user.getNickname() == null ? user.getUsername() : user.getNickname()) + "的部族组");
    group.setOwnerId(user.getId());
    group.setStatus(1);
    clanGroupMapper.insert(group);
    return user;
  }

  public Map<String, Object> info(AuthUser current) {
    SysUser user = userMapper.selectById(current.getUserId());
    Map<String, Object> data = new HashMap<>();
    AuthUser au = user == null ? current : toAuthUser(user);
    data.put("user", au);
    data.put("menus", buildMenuTree(menuMapper.selectList(new QueryWrapper<SysMenu>().orderByAsc("sort")), au));
    return data;
  }

  /** 将 SysUser 转换为携带角色与权限的 AuthUser */
  public AuthUser toAuthUser(SysUser user) {
    AuthUser au = new AuthUser();
    au.setUserId(user.getId());
    au.setUsername(user.getUsername());
    au.setGroupNo(user.getGroupNo());
    au.setSuperAdmin(user.getGroupNo() == null || user.getGroupNo().isEmpty());

    List<SysUserRole> userRoles = userRoleMapper.selectList(new QueryWrapper<SysUserRole>().eq("user_id", user.getId()));
    Set<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());

    if (!roleIds.isEmpty()) {
      List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
      for (SysRole role : roles) {
        au.getRoleCodes().add(role.getRoleCode());
        if (RoleConstants.SUPER_ADMIN.equals(role.getRoleCode())) {
          au.setSuperAdmin(true);
        }
      }
      List<SysRoleMenu> roleMenus = roleMenuMapper.selectList(new QueryWrapper<SysRoleMenu>().in("role_id", roleIds));
      Set<Long> menuIds = roleMenus.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toSet());
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
    List<SysMenu> filtered = all;
    if (!current.isSuperAdmin()) {
      final Set<String> perms = current.getPermissions();
      filtered = all.stream()
          .filter(m -> m.getPermission() == null || perms.contains(m.getPermission()))
          .collect(Collectors.toList());
    }
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
      node.setChildren(new ArrayList<>());
      nodeMap.put(m.getId(), node);
    }
    List<MenuNode> roots = new ArrayList<>();
    for (MenuNode node : nodeMap.values()) {
      MenuNode parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
      if (parent == null) {
        roots.add(node);
      } else {
        parent.getChildren().add(node);
      }
    }
    roots.sort((a, b) -> Long.compare(a.getId() == null ? 0 : a.getId(), b.getId() == null ? 0 : b.getId()));
    return roots;
  }

  private String generateGroupNo() {
    return "G" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
  }
}
