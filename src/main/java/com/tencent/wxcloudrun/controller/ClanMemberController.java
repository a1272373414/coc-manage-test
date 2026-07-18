package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/clan/member")
public class ClanMemberController extends BaseCrudController<ClanMember> {

  @Resource
  private ClanMemberMapper clanMemberMapper;

  @Override
  protected BaseMapper<ClanMember> mapper() {
    return clanMemberMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("member_name", "member_no", "clan_no");
  }
}
