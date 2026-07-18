package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.entity.sys.SysUser;
import com.tencent.wxcloudrun.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/sys/user")
public class SysUserController extends BaseCrudController<SysUser> {

  @Resource
  private SysUserMapper sysUserMapper;

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

  @Override
  @PostMapping
  public ApiResponse create(@RequestBody SysUser body) {
    if (body.getPassword() != null && !body.getPassword().isEmpty()) {
      body.setPassword(encoder.encode(body.getPassword()));
    } else {
      body.setPassword(encoder.encode("123456"));
    }
    body.setId(null);
    mapper().insert(body);
    return ApiResponse.ok(body);
  }

  @Override
  @PutMapping
  public ApiResponse update(@RequestBody SysUser body) {
    if (body.getId() == null) {
      return ApiResponse.error("id 不能为空");
    }
    if (body.getPassword() != null && !body.getPassword().isEmpty()) {
      body.setPassword(encoder.encode(body.getPassword()));
    }
    mapper().updateById(body);
    return ApiResponse.ok(body);
  }
}
