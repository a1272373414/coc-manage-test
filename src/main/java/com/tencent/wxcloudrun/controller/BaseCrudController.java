package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.entity.BaseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 通用 CRUD 控制器基类。子类需提供 Mapper 与可模糊检索的字段，并继承后加上
 * {@code @RestController} 与 {@code @RequestMapping}。所有写操作都会经过多租户插件按
 * group_no 自动隔离。子类可覆盖 create/update 增加自定义逻辑（如密码加密）。
 */
public abstract class BaseCrudController<Entity extends BaseEntity> {

  /** 子类提供具体实体的 Mapper。 */
  protected abstract BaseMapper<Entity> mapper();

  /** 列表模糊检索字段，子类按需覆盖。 */
  protected List<String> keywordFields() {
    return java.util.Collections.emptyList();
  }

  @GetMapping("/page")
  public ApiResponse page(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size) {
    Page<Entity> page = PageResult.page(current, size);
    QueryWrapper<Entity> qw = new QueryWrapper<>();
    List<String> fields = keywordFields();
    if (keyword != null && !keyword.trim().isEmpty() && !fields.isEmpty()) {
      String kw = keyword.trim();
      qw.and(w -> {
        boolean first = true;
        for (String field : fields) {
          if (!first) {
            w.or();
          }
          w.like(field, kw);
          first = false;
        }
      });
    }
    qw.orderByDesc("id");
    mapper().selectPage(page, qw);
    return ApiResponse.ok(PageResult.of(page));
  }

  @GetMapping("/{id}")
  public ApiResponse getById(@PathVariable Long id) {
    return ApiResponse.ok(mapper().selectById(id));
  }

  @PostMapping
  public ApiResponse create(@RequestBody Entity body) {
    body.setId(null);
    mapper().insert(body);
    return ApiResponse.ok(body);
  }

  @PutMapping
  public ApiResponse update(@RequestBody Entity body) {
    if (body.getId() == null) {
      return ApiResponse.error("id 不能为空");
    }
    mapper().updateById(body);
    return ApiResponse.ok(body);
  }

  @DeleteMapping("/{id}")
  public ApiResponse delete(@PathVariable Long id) {
    mapper().deleteById(id);
    return ApiResponse.ok();
  }
}
