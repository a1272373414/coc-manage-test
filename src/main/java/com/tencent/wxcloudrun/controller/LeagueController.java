package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.biz.League;
import com.tencent.wxcloudrun.mapper.LeagueMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/league")
public class LeagueController extends BaseCrudController<League> {

  @Resource
  private LeagueMapper leagueMapper;

  @Override
  protected BaseMapper<League> mapper() {
    return leagueMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("league_name", "league_no", "tier");
  }
}
