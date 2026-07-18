package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.biz.LeagueRecord;
import com.tencent.wxcloudrun.mapper.LeagueRecordMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/league/record")
public class LeagueRecordController extends BaseCrudController<LeagueRecord> {

  @Resource
  private LeagueRecordMapper leagueRecordMapper;

  @Override
  protected BaseMapper<LeagueRecord> mapper() {
    return leagueRecordMapper;
  }
}
