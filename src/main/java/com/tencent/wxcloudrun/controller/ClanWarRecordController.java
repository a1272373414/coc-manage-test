package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.biz.ClanWarRecord;
import com.tencent.wxcloudrun.mapper.ClanWarRecordMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/war/record")
public class ClanWarRecordController extends BaseCrudController<ClanWarRecord> {

  @Resource
  private ClanWarRecordMapper clanWarRecordMapper;

  @Override
  protected BaseMapper<ClanWarRecord> mapper() {
    return clanWarRecordMapper;
  }
}
