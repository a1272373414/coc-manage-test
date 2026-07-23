package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.entity.dict.DictItem;
import com.tencent.wxcloudrun.mapper.DictItemMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/dict/item")
public class DictItemController extends BaseCrudController<DictItem> {

  @Resource
  private DictItemMapper dictItemMapper;

  @Override
  protected BaseMapper<DictItem> mapper() {
    return dictItemMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("group_code", "item_value", "item_name");
  }

  /**
   * 覆盖父类分页方法（签名完全一致以保证 @Override 通过、避免 Ambiguous mapping）：
   * - 额外从请求参数中读取 groupCode，按字典组精确过滤
   * - 按 sort 升序排列
   * 前端通过 COC.api.dictItems(groupCode) 加载下拉选项时传 groupCode + size=9999。
   */
  @Override
  @GetMapping("/page")
  public ApiResponse page(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size) {
    Page<DictItem> page = PageResult.page(current, size);
    QueryWrapper<DictItem> qw = new QueryWrapper<>();
    // 从请求参数中读取 groupCode（父类方法签名不支持直接加参数）
    HttpServletRequest req = ((org.springframework.web.context.request.ServletRequestAttributes)
        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest();
    String groupCode = req.getParameter("groupCode");
    if (groupCode != null && !groupCode.trim().isEmpty()) {
      qw.eq("group_code", groupCode.trim());
    }
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
    qw.orderByAsc("sort");
    mapper().selectPage(page, qw);
    return ApiResponse.ok(PageResult.of(page));
  }
}
