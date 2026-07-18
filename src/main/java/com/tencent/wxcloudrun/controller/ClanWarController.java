package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.biz.ClanWar;
import com.tencent.wxcloudrun.mapper.ClanWarMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/war")
public class ClanWarController extends BaseCrudController<ClanWar> {

  @Resource
  private ClanWarMapper clanWarMapper;

  @Override
  protected BaseMapper<ClanWar> mapper() {
    return clanWarMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("war_no", "clan_no", "win_status");
  }
}
