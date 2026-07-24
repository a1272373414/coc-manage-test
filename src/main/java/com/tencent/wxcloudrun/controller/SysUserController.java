package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.sys.SysUser;
import com.tencent.wxcloudrun.entity.sys.SysUserRole;
import com.tencent.wxcloudrun.mapper.SysUserMapper;
import com.tencent.wxcloudrun.mapper.SysUserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sys/user")
@SuppressWarnings("all")
public class SysUserController extends BaseCrudController<SysUser> {

  @Resource
  private SysUserMapper sysUserMapper;
  @Resource
  private SysUserRoleMapper userRoleMapper;

  private final PasswordEncoder encoder =
      new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

  @Override
  protected BaseMapper<SysUser> mapper() {
    return sysUserMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("username", "nickname", "phone");
  }

  /** 重写分页接口：分页后批量查询 sys_user_role 填充 roleIds，避免 N+1 */
  @Override
  @GetMapping("/page")
  public ApiResponse page(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size) {
    Page<SysUser> page = PageResult.page(current, size);
    QueryWrapper<SysUser> qw = new QueryWrapper<>();
    List<String> fields = keywordFields();
    if (keyword != null && !keyword.trim().isEmpty() && !fields.isEmpty()) {
      String kw = keyword.trim();
      qw.and(w -> {
        boolean first = true;
        for (String field : fields) {
          if (!first) w.or();
          w.like(field, kw);
          first = false;
        }
      });
    }
    qw.orderByDesc("id");
    // 非超级管理员仅展示本群组用户
    if (!UserContext.isSuperAdmin()) {
      String groupNo = UserContext.getGroupNo();
      qw.eq("group_no", groupNo == null ? "" : groupNo);
    }
    mapper().selectPage(page, qw);

    List<SysUser> records = page.getRecords();
    fillRoleIds(records);
    return ApiResponse.ok(PageResult.of(page));
  }

  /** 重写详情接口：填充 roleIds，非超管时校验用户所属群组 */
  @Override
  @GetMapping("/{id}")
  public ApiResponse getById(@PathVariable Long id) {
    SysUser user = mapper().selectById(id);
    if (user != null) {
      // 非超级管理员只能查看本群组用户
      if (!UserContext.isSuperAdmin()) {
        String groupNo = UserContext.getGroupNo();
        if (!Objects.equals(groupNo, user.getGroupNo())) {
          return ApiResponse.error("无权查看该用户");
        }
      }
      fillRoleIds(Collections.singletonList(user));
    }
    return ApiResponse.ok(user);
  }

  /** 批量查询 sys_user_role，回填每个用户的 roleIds */
  @SuppressWarnings("all")
  private void fillRoleIds(List<SysUser> users) {
    if (users == null || users.isEmpty()) return;
    Set<Long> userIds = users.stream()
        .filter(java.util.Objects::nonNull)
        .map(SysUser::getId)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
    if (userIds.isEmpty()) return;
    List<SysUserRole> all = userRoleMapper.selectList(
        new QueryWrapper<SysUserRole>().in("user_id", userIds));
    Map<Long, List<Long>> userIdToRoleIds = new HashMap<>();
    for (SysUserRole ur : all) {
      userIdToRoleIds.computeIfAbsent(ur.getUserId(), k -> new java.util.ArrayList<>())
          .add(ur.getRoleId());
    }
    for (SysUser u : users) {
      u.setRoleIds(userIdToRoleIds.getOrDefault(u.getId(), Collections.emptyList()));
    }
  }

  @Override
  @PostMapping
  public ApiResponse create(@RequestBody SysUser body) {
    if (body.getPassword() != null && !body.getPassword().isEmpty()) {
      body.setPassword(encoder.encode(body.getPassword()));
    } else {
      body.setPassword(encoder.encode("123456"));
    }
    body.setId(null);
    // 非超级管理员创建用户时，自动设置 group_no 为当前用户的 group_no
    if (!UserContext.isSuperAdmin()) {
      body.setGroupNo(UserContext.getGroupNo());
    }
    mapper().insert(body);
    return ApiResponse.ok(body);
  }

  @Override
  @PutMapping
  public ApiResponse update(@RequestBody SysUser body) {
    if (body.getId() == null) {
      return ApiResponse.error("id 不能为空");
    }
    // 非超级管理员只能更新本群组用户
    if (!UserContext.isSuperAdmin()) {
      SysUser existing = mapper().selectById(body.getId());
      if (existing == null) {
        return ApiResponse.error("用户不存在");
      }
      String groupNo = UserContext.getGroupNo();
      if (!Objects.equals(groupNo, existing.getGroupNo())) {
        return ApiResponse.error("无权修改该用户");
      }
      // 防止越权修改 group_no
      body.setGroupNo(existing.getGroupNo());
    }
    if (body.getPassword() != null && !body.getPassword().isEmpty()) {
      body.setPassword(encoder.encode(body.getPassword()));
    }
    mapper().updateById(body);
    return ApiResponse.ok(body);
  }
}
