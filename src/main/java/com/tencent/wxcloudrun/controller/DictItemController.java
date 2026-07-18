package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.dict.DictItem;
import com.tencent.wxcloudrun.mapper.DictItemMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
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
}
