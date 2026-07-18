package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.sys.SysMenu;
import com.tencent.wxcloudrun.mapper.SysMenuMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/sys/menu")
public class SysMenuController extends BaseCrudController<SysMenu> {

  @Resource
  private SysMenuMapper sysMenuMapper;

  @Override
  protected BaseMapper<SysMenu> mapper() {
    return sysMenuMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("menu_name", "permission");
  }
}
