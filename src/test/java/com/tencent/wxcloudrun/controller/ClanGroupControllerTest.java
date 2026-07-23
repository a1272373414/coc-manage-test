package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.RoleConstants;
import com.tencent.wxcloudrun.entity.biz.ClanGroup;
import com.tencent.wxcloudrun.entity.sys.SysRole;
import com.tencent.wxcloudrun.entity.sys.SysUser;
import com.tencent.wxcloudrun.entity.sys.SysUserRole;
import com.tencent.wxcloudrun.mapper.ClanGroupMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMapper;
import com.tencent.wxcloudrun.mapper.SysUserMapper;
import com.tencent.wxcloudrun.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ClanGroupController 单元测试：
 * 聚焦群主绑定/解绑逻辑（create/update 中同步更新 sys_user + sys_user_role）。
 */
@DisplayName("群组控制器测试 - 群主绑定/解绑")
@ExtendWith(MockitoExtension.class)
class ClanGroupControllerTest {

  @Mock
  private ClanGroupMapper clanGroupMapper;
  @Mock
  private SysUserMapper sysUserMapper;
  @Mock
  private SysRoleMapper sysRoleMapper;
  @Mock
  private SysUserRoleMapper sysUserRoleMapper;

  @InjectMocks
  private ClanGroupController controller;

  private SysRole ownerRole() {
    SysRole r = new SysRole();
    r.setId(30L);
    r.setRoleCode(RoleConstants.GROUP_ADMIN);
    return r;
  }

  private SysUser user(Long id, String groupNo) {
    SysUser u = new SysUser();
    u.setId(id);
    u.setGroupNo(groupNo);
    u.setUsername("user" + id);
    return u;
  }

  // ==================== create - 绑定群主 ====================

  @Test
  @DisplayName("create - 指定 ownerId 时同步绑定群主（更新 user.groupNo + 添加角色）")
  void create_withOwner_binds() {
    ClanGroup group = new ClanGroup();
    group.setGroupNo("G001");
    group.setOwnerId(5L);

    when(sysUserMapper.selectById(5L)).thenReturn(user(5L, null));
    when(sysRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(ownerRole());
    when(sysUserRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

    ApiResponse resp = controller.create(group);

    assertEquals(0, resp.getCode());
    // 验证用户 groupNo 被更新
    ArgumentCaptor<SysUser> userCap = ArgumentCaptor.forClass(SysUser.class);
    verify(sysUserMapper).updateById(userCap.capture());
    assertEquals("G001", userCap.getValue().getGroupNo());
    // 验证角色绑定被插入
    ArgumentCaptor<SysUserRole> roleCap = ArgumentCaptor.forClass(SysUserRole.class);
    verify(sysUserRoleMapper).insert(roleCap.capture());
    assertEquals(Long.valueOf(5L), roleCap.getValue().getUserId());
    assertEquals(Long.valueOf(30L), roleCap.getValue().getRoleId());
  }

  @Test
  @DisplayName("create - ownerId 为 null 时不绑定群主")
  void create_noOwner_noBinding() {
    ClanGroup group = new ClanGroup();
    group.setGroupNo("G001");
    group.setOwnerId(null);

    controller.create(group);

    verify(sysUserMapper, never()).updateById(any());
    verify(sysUserRoleMapper, never()).insert(any());
  }

  @Test
  @DisplayName("create - 用户已有 GROUP_ADMIN 角色时不重复插入")
  void create_ownerRoleExists_noDuplicateInsert() {
    ClanGroup group = new ClanGroup();
    group.setGroupNo("G001");
    group.setOwnerId(5L);

    when(sysUserMapper.selectById(5L)).thenReturn(user(5L, null));
    when(sysRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(ownerRole());

    SysUserRole existingBinding = new SysUserRole();
    existingBinding.setUserId(5L);
    existingBinding.setRoleId(30L);
    when(sysUserRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(existingBinding);

    controller.create(group);

    // 用户 groupNo 仍然更新
    verify(sysUserMapper).updateById(any(SysUser.class));
    // 但角色绑定不重复插入
    verify(sysUserRoleMapper, never()).insert(any());
  }

  // ==================== update - 群主变更 ====================

  @Test
  @DisplayName("update - ownerId 变更时解绑旧群主 + 绑定新群主")
  void update_ownerChanged_unbindsOldAndBindsNew() {
    ClanGroup old = new ClanGroup();
    old.setId(1L);
    old.setOwnerId(5L);
    old.setGroupNo("G001");

    ClanGroup body = new ClanGroup();
    body.setId(1L);
    body.setOwnerId(10L); // 变更群主
    body.setGroupNo("G001");

    when(clanGroupMapper.selectById(1L)).thenReturn(old);
    when(sysUserMapper.selectById(5L)).thenReturn(user(5L, "G001")); // 旧群主
    when(sysUserMapper.selectById(10L)).thenReturn(user(10L, null)); // 新群主
    when(sysRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(ownerRole());
    when(sysUserRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

    controller.update(body);

    // 验证旧群主解绑：groupNo 清空 + 删除角色绑定
    ArgumentCaptor<SysUser> oldUserCap = ArgumentCaptor.forClass(SysUser.class);
    verify(sysUserMapper, times(2)).updateById(oldUserCap.capture());
    // 第一次调用是旧群主（清空 groupNo），第二次是新群主（设置 groupNo）
    // Mockito 按调用顺序存储，但 captor.getAllValues 更安全
    java.util.List<SysUser> updatedUsers = oldUserCap.getAllValues();
    assertEquals(2, updatedUsers.size());
    // 旧群主 groupNo 被清空
    assertNull(updatedUsers.get(0).getGroupNo());
    // 新群主 groupNo 被设置
    assertEquals("G001", updatedUsers.get(1).getGroupNo());

    // 验证旧群主角色绑定被删除
    verify(sysUserRoleMapper).delete(any(QueryWrapper.class));
    // 验证新群主角色绑定被插入
    verify(sysUserRoleMapper).insert(any(SysUserRole.class));
  }

  @Test
  @DisplayName("update - ownerId 未变更时不触发绑定/解绑")
  void update_ownerUnchanged_noBindingChange() {
    ClanGroup old = new ClanGroup();
    old.setId(1L);
    old.setOwnerId(5L);

    ClanGroup body = new ClanGroup();
    body.setId(1L);
    body.setOwnerId(5L); // 同一个 ownerId

    when(clanGroupMapper.selectById(1L)).thenReturn(old);

    controller.update(body);

    verify(sysUserMapper, never()).updateById(any());
    verify(sysUserRoleMapper, never()).insert(any());
    verify(sysUserRoleMapper, never()).delete(any());
  }

  @Test
  @DisplayName("update - 旧 ownerId 为 null 新 ownerId 有值时只绑定不解绑")
  void update_fromNullToOwner_onlyBinds() {
    ClanGroup old = new ClanGroup();
    old.setId(1L);
    old.setOwnerId(null);

    ClanGroup body = new ClanGroup();
    body.setId(1L);
    body.setOwnerId(5L);
    body.setGroupNo("G001");

    when(clanGroupMapper.selectById(1L)).thenReturn(old);
    when(sysUserMapper.selectById(5L)).thenReturn(user(5L, null));
    when(sysRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(ownerRole());
    when(sysUserRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

    controller.update(body);

    // 只绑定，不删除
    verify(sysUserMapper, times(1)).updateById(any(SysUser.class));
    verify(sysUserRoleMapper, never()).delete(any());
    verify(sysUserRoleMapper, times(1)).insert(any(SysUserRole.class));
  }

  @Test
  @DisplayName("update - 新 ownerId 为 null 时只解绑不绑定")
  void update_fromOwnerToNull_onlyUnbinds() {
    ClanGroup old = new ClanGroup();
    old.setId(1L);
    old.setOwnerId(5L);

    ClanGroup body = new ClanGroup();
    body.setId(1L);
    body.setOwnerId(null); // 移除群主

    when(clanGroupMapper.selectById(1L)).thenReturn(old);
    when(sysUserMapper.selectById(5L)).thenReturn(user(5L, "G001"));
    when(sysRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(ownerRole());

    controller.update(body);

    // 只解绑，不插入
    verify(sysUserMapper, times(1)).updateById(any(SysUser.class));
    verify(sysUserRoleMapper, times(1)).delete(any(QueryWrapper.class));
    verify(sysUserRoleMapper, never()).insert(any());
  }

  @Test
  @DisplayName("update - id 为 null 时返回错误")
  void update_nullId_returnsError() {
    ClanGroup body = new ClanGroup();
    body.setId(null);

    ApiResponse resp = controller.update(body);

    assertEquals(400, resp.getCode());
    verify(clanGroupMapper, never()).updateById(any());
  }

  // ==================== bindOwner 边界 ====================

  @Test
  @DisplayName("create - 用户不存在时只跳过 groupNo 更新但仍尝试角色绑定")
  void create_userNotFound_skipsUserUpdateButAttemptsRole() {
    ClanGroup group = new ClanGroup();
    group.setGroupNo("G001");
    group.setOwnerId(999L);

    when(sysUserMapper.selectById(999L)).thenReturn(null);
    when(sysRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(ownerRole());
    when(sysUserRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

    controller.create(group);

    verify(sysUserMapper, never()).updateById(any());
    verify(sysUserRoleMapper).insert(any(SysUserRole.class));
  }

  @Test
  @DisplayName("create - GROUP_ADMIN 角色不存在时跳过角色绑定")
  void create_ownerRoleNotFound_skipsRoleBinding() {
    ClanGroup group = new ClanGroup();
    group.setGroupNo("G001");
    group.setOwnerId(5L);

    when(sysUserMapper.selectById(5L)).thenReturn(user(5L, null));
    when(sysRoleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null); // 角色不存在

    controller.create(group);

    // 用户 groupNo 仍然更新
    verify(sysUserMapper).updateById(any(SysUser.class));
    // 但角色绑定不执行
    verify(sysUserRoleMapper, never()).insert(any());
  }
}
