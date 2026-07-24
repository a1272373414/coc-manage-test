package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.AuthUser;
import com.tencent.wxcloudrun.config.RoleConstants;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.ClanGroup;
import com.tencent.wxcloudrun.entity.sys.SysRole;
import com.tencent.wxcloudrun.entity.sys.SysUser;
import com.tencent.wxcloudrun.entity.sys.SysUserRole;
import com.tencent.wxcloudrun.mapper.ClanGroupMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMapper;
import com.tencent.wxcloudrun.mapper.SysUserMapper;
import com.tencent.wxcloudrun.mapper.SysUserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 群组成员管理。
 * 群主/部落组管理员可查看本群组成员、设置为部落管理员、踢出成员。
 */
@RestController
@RequestMapping("/api/clan/group/user")
public class ClanGroupMemberController {

  @Autowired
  private SysUserMapper userMapper;
  @Autowired
  private SysUserRoleMapper userRoleMapper;
  @Autowired
  private SysRoleMapper roleMapper;
  @Autowired
  private ClanGroupMapper clanGroupMapper;

  /**
   * 分页查询当前用户所属群组下的成员。
   * - 超级管理员：可查看所有有群组的用户
   * - 群主/部落组管理员：可查看所管理群组下的用户
   * - 其他角色：可查看本群组成员
   */
  @GetMapping("/page")
  public ApiResponse page(@RequestParam(defaultValue = "1") int current,
                           @RequestParam(defaultValue = "10") int size) {
    AuthUser user = UserContext.get();
    if (user == null) return ApiResponse.error("请先登录");

    Page<SysUser> page = new Page<>(current, size);
    QueryWrapper<SysUser> qw = new QueryWrapper<>();

    if (user.isSuperAdmin()) {
      // 超管：可查看所有有群组的用户（group_no 不为空）
      qw.isNotNull("group_no").ne("group_no", "");
    } else if (user.getRoleCodes().contains(RoleConstants.GROUP_ADMIN)) {
      Set<String> groupNos = managedGroupNos(user);
      if (groupNos.isEmpty()) {
        Map<String, Object> empty = new HashMap<>();
        empty.put("records", Collections.emptyList());
        empty.put("total", 0L);
        empty.put("current", (long) current);
        empty.put("size", (long) size);
        return ApiResponse.ok(empty);
      }
      qw.in("group_no", new ArrayList<>(groupNos));
    } else if (user.getGroupNo() != null && !user.getGroupNo().isEmpty()) {
      qw.eq("group_no", user.getGroupNo());
    } else {
      Map<String, Object> empty = new HashMap<>();
      empty.put("records", Collections.emptyList());
      empty.put("total", 0L);
      empty.put("current", (long) current);
      empty.put("size", (long) size);
      return ApiResponse.ok(empty);
    }

    qw.orderByDesc("id");
    userMapper.selectPage(page, qw);
    List<SysUser> records = page.getRecords();
    fillRoleCodes(records);

    List<Map<String, Object>> list = new ArrayList<>();
    for (SysUser u : records) {
      Map<String, Object> m = new HashMap<>();
      m.put("id", u.getId());
      m.put("username", u.getUsername());
      m.put("nickname", u.getNickname());
      m.put("groupNo", u.getGroupNo());
      m.put("status", u.getStatus());
      m.put("roleCodes", u.getRoleCodes() != null ? u.getRoleCodes() : Collections.emptyList());
      list.add(m);
    }

    Map<String, Object> result = new HashMap<>();
    result.put("records", list);
    result.put("total", page.getTotal());
    result.put("current", page.getCurrent());
    result.put("size", page.getSize());
    return ApiResponse.ok(result);
  }

  /**
   * 设置成员为部落管理员（LEAGUE_ADMIN）。
   * 仅群主/超管可操作，不能操作自己。
   */
  @PutMapping("/{userId}/set-admin")
  public ApiResponse setAdmin(@PathVariable Long userId) {
    AuthUser user = UserContext.get();
    if (user == null) return ApiResponse.error("请先登录");
    if (user.getUserId().equals(userId)) return ApiResponse.error("不能操作自己");
    if (!canManage(user, userId)) return ApiResponse.error("无权限操作该成员");

    SysRole leagueAdminRole = roleMapper.selectOne(
        new QueryWrapper<SysRole>().eq("role_code", RoleConstants.LEAGUE_ADMIN));
    if (leagueAdminRole == null || leagueAdminRole.getId() == null) {
      return ApiResponse.error("部落管理员角色不存在");
    }

    QueryWrapper<SysUserRole> qw = new QueryWrapper<>();
    qw.eq("user_id", userId).eq("role_id", leagueAdminRole.getId());
    if (userRoleMapper.selectCount(qw) == 0) {
      SysUserRole ur = new SysUserRole();
      ur.setUserId(userId);
      ur.setRoleId(leagueAdminRole.getId());
      userRoleMapper.insert(ur);
    }

    return ApiResponse.ok();
  }

  /**
   * 取消成员的部落管理员身份（移除 LEAGUE_ADMIN 绑定）。
   * 仅群主/超管可操作，不能操作自己；取消后若无任何角色则恢复为普通成员。
   */
  @PutMapping("/{userId}/cancel-admin")
  public ApiResponse cancelAdmin(@PathVariable Long userId) {
    AuthUser user = UserContext.get();
    if (user == null) return ApiResponse.error("请先登录");
    if (user.getUserId().equals(userId)) return ApiResponse.error("不能操作自己");
    if (!canManage(user, userId)) return ApiResponse.error("无权限操作该成员");

    SysRole leagueAdminRole = roleMapper.selectOne(
        new QueryWrapper<SysRole>().eq("role_code", RoleConstants.LEAGUE_ADMIN));
    if (leagueAdminRole == null || leagueAdminRole.getId() == null) {
      return ApiResponse.error("部落管理员角色不存在");
    }

    userRoleMapper.delete(new QueryWrapper<SysUserRole>()
        .eq("user_id", userId).eq("role_id", leagueAdminRole.getId()));

    // 取消后若用户无任何角色，恢复为普通成员，避免成为无角色用户
    long remain = userRoleMapper.selectCount(new QueryWrapper<SysUserRole>().eq("user_id", userId));
    if (remain == 0) {
      SysRole memberRole = roleMapper.selectOne(
          new QueryWrapper<SysRole>().eq("role_code", RoleConstants.MEMBER));
      if (memberRole != null && memberRole.getId() != null) {
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(memberRole.getId());
        userRoleMapper.insert(ur);
      }
    }

    return ApiResponse.ok();
  }

  /**
   * 踢出成员：清空群组编号、删除所有角色、恢复为游客。
   * 仅群主/超管可操作，不能操作自己。
   */
  @PutMapping("/{userId}/kick")
  public ApiResponse kick(@PathVariable Long userId) {
    AuthUser user = UserContext.get();
    if (user == null) return ApiResponse.error("请先登录");
    if (user.getUserId().equals(userId)) return ApiResponse.error("不能操作自己");
    if (!canManage(user, userId)) return ApiResponse.error("无权限操作该成员");

    SysUser target = userMapper.selectById(userId);
    if (target == null) return ApiResponse.error("成员不存在");

    // 清空群组编号
    target.setGroupNo(null);
    userMapper.updateById(target);

    // 删除所有角色绑定
    userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", userId));

    // 恢复为游客
    SysRole visitorRole = roleMapper.selectOne(
        new QueryWrapper<SysRole>().eq("role_code", RoleConstants.VISITOR));
    if (visitorRole != null && visitorRole.getId() != null) {
      SysUserRole ur = new SysUserRole();
      ur.setUserId(userId);
      ur.setRoleId(visitorRole.getId());
      userRoleMapper.insert(ur);
    }

    return ApiResponse.ok();
  }

  // ==================== 私有辅助方法 ====================

  /** 获取当前用户可管理的所有群组编号 */
  private Set<String> managedGroupNos(AuthUser user) {
    Set<String> set = new HashSet<>();
    if (user.getGroupNo() != null && !user.getGroupNo().isEmpty()) {
      set.add(user.getGroupNo());
    }
    List<ClanGroup> owned = clanGroupMapper.selectList(
        new QueryWrapper<ClanGroup>().eq("owner_id", user.getUserId()));
    for (ClanGroup g : owned) {
      if (g.getGroupNo() != null && !g.getGroupNo().isEmpty()) {
        set.add(g.getGroupNo());
      }
    }
    return set;
  }

  /** 判断当前用户是否有权管理目标用户 */
  private boolean canManage(AuthUser manager, Long targetUserId) {
    if (manager.isSuperAdmin()) return true;
    SysUser target = userMapper.selectById(targetUserId);
    if (target == null || target.getGroupNo() == null) return false;
    return managedGroupNos(manager).contains(target.getGroupNo());
  }

  /** 批量填充用户的角色编码列表 */
  private void fillRoleCodes(List<SysUser> records) {
    if (records == null || records.isEmpty()) return;
    Set<Long> userIds = new HashSet<>();
    for (SysUser u : records) userIds.add(u.getId());

    List<SysUserRole> allUR = userRoleMapper.selectList(
        new QueryWrapper<SysUserRole>().in("user_id", new ArrayList<>(userIds)));
    if (allUR.isEmpty()) return;

    Set<Long> roleIds = new HashSet<>();
    for (SysUserRole ur : allUR) roleIds.add(ur.getRoleId());

    List<SysRole> roles = roleMapper.selectBatchIds(new ArrayList<>(roleIds));
    Map<Long, String> roleIdToCode = new HashMap<>();
    for (SysRole r : roles) {
      if (r.getRoleCode() != null) roleIdToCode.put(r.getId(), r.getRoleCode());
    }

    Map<Long, List<String>> userRoles = new HashMap<>();
    for (SysUserRole ur : allUR) {
      String code = roleIdToCode.get(ur.getRoleId());
      if (code != null) {
        userRoles.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(code);
      }
    }

    for (SysUser u : records) {
      u.setRoleCodes(userRoles.getOrDefault(u.getId(), Collections.emptyList()));
    }
  }
}
