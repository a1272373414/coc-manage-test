package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.biz.ClanGroup;
import com.tencent.wxcloudrun.mapper.ClanGroupMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/clan/group")
public class ClanGroupController extends BaseCrudController<ClanGroup> {

  @Resource
  private ClanGroupMapper clanGroupMapper;

  @Override
  protected BaseMapper<ClanGroup> mapper() {
    return clanGroupMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("group_no", "group_name");
  }
}
