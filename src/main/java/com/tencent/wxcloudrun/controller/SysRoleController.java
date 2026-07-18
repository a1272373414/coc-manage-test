package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.sys.SysRole;
import com.tencent.wxcloudrun.mapper.SysRoleMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/sys/role")
public class SysRoleController extends BaseCrudController<SysRole> {

  @Resource
  private SysRoleMapper sysRoleMapper;

  @Override
  protected BaseMapper<SysRole> mapper() {
    return sysRoleMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("role_code", "role_name");
  }
}
