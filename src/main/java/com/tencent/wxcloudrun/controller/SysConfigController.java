package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.sys.SysConfig;
import com.tencent.wxcloudrun.mapper.SysConfigMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 系统配置：仅超级管理员可操作；删除为物理删除（实体未使用 @TableLogic，deleteById 即物理删除）。
 * 不继承 BaseCrudController，避免其要求 Entity 继承 BaseEntity（含 @TableLogic 逻辑删除）。
 */
@RestController
@RequestMapping("/api/sys/config")
@SuppressWarnings("all")
public class SysConfigController {

  @Resource
  private SysConfigMapper sysConfigMapper;

  /** 仅超级管理员可操作，否则拒绝 */
  private ApiResponse denyIfNotSuper() {
    if (!UserContext.isSuperAdmin()) {
      return ApiResponse.error(403, "仅超级管理员可操作");
    }
    return null;
  }

  private QueryWrapper<SysConfig> buildKeywordWrapper(String keyword) {
    QueryWrapper<SysConfig> qw = new QueryWrapper<>();
    if (keyword != null && !keyword.trim().isEmpty()) {
      String kw = keyword.trim();
      qw.and(w -> w.like("config_name", kw).or().like("config_value", kw).or().like("description", kw));
    }
    return qw;
  }

  @GetMapping("/page")
  public ApiResponse page(@RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "1") long current,
                          @RequestParam(defaultValue = "10") long size) {
    ApiResponse deny = denyIfNotSuper();
    if (deny != null) return deny;
    QueryWrapper<SysConfig> qw = buildKeywordWrapper(keyword);
    qw.orderByDesc("id");
    Page<SysConfig> page = PageResult.page(current, size);
    sysConfigMapper.selectPage(page, qw);
    return ApiResponse.ok(PageResult.of(page));
  }

  @GetMapping("/list")
  public ApiResponse list(@RequestParam(required = false) String keyword) {
    ApiResponse deny = denyIfNotSuper();
    if (deny != null) return deny;
    QueryWrapper<SysConfig> qw = buildKeywordWrapper(keyword);
    qw.orderByDesc("id");
    return ApiResponse.ok(sysConfigMapper.selectList(qw));
  }

  @GetMapping("/{id}")
  public ApiResponse getById(@PathVariable Long id) {
    ApiResponse deny = denyIfNotSuper();
    if (deny != null) return deny;
    return ApiResponse.ok(sysConfigMapper.selectById(id));
  }

  @PostMapping
  public ApiResponse create(@RequestBody SysConfig body) {
    ApiResponse deny = denyIfNotSuper();
    if (deny != null) return deny;
    if (body.getConfigName() == null || body.getConfigName().trim().isEmpty()) {
      return ApiResponse.error("配置名不能为空");
    }
    String name = body.getConfigName().trim();
    SysConfig exist = sysConfigMapper.selectOne(new QueryWrapper<SysConfig>().eq("config_name", name));
    if (exist != null) {
      return ApiResponse.error("配置名已存在");
    }
    body.setId(null);
    body.setConfigName(name);
    if (body.getConfigValue() == null) body.setConfigValue("");
    sysConfigMapper.insert(body);
    return ApiResponse.ok(body);
  }

  @PutMapping
  public ApiResponse update(@RequestBody SysConfig body) {
    ApiResponse deny = denyIfNotSuper();
    if (deny != null) return deny;
    if (body.getId() == null) {
      return ApiResponse.error("ID 不能为空");
    }
    sysConfigMapper.updateById(body);
    return ApiResponse.ok(body);
  }

  @DeleteMapping("/{id}")
  public ApiResponse delete(@PathVariable Long id) {
    ApiResponse deny = denyIfNotSuper();
    if (deny != null) return deny;
    sysConfigMapper.deleteById(id); // 物理删除
    return ApiResponse.ok();
  }
}
