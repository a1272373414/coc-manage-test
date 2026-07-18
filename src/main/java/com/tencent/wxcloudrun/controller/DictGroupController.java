package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.dict.DictGroup;
import com.tencent.wxcloudrun.mapper.DictGroupMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/dict/group")
public class DictGroupController extends BaseCrudController<DictGroup> {

  @Resource
  private DictGroupMapper dictGroupMapper;

  @Override
  protected BaseMapper<DictGroup> mapper() {
    return dictGroupMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("group_code", "group_name");
  }
}
