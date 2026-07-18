package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.entity.biz.LeagueSignup;
import com.tencent.wxcloudrun.mapper.LeagueSignupMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/league/signup")
public class LeagueSignupController {

  @Resource
  private LeagueSignupMapper signupMapper;

  /** 某联赛的报名名单（受 group_no 隔离约束） */
  @GetMapping("/list")
  public ApiResponse list(@RequestParam String leagueNo) {
    List<LeagueSignup> list = signupMapper.selectList(
        new QueryWrapper<LeagueSignup>().eq("league_no", leagueNo).orderByDesc("id"));
    return ApiResponse.ok(list);
  }

  /** 报名 / 退赛（按 league_no + member_no 幂等更新） */
  @PostMapping
  public ApiResponse signup(@RequestBody LeagueSignup body) {
    if (body.getLeagueNo() == null || body.getMemberNo() == null) {
      return ApiResponse.error("leagueNo 与 memberNo 不能为空");
    }
    LeagueSignup existing = signupMapper.selectOne(new QueryWrapper<LeagueSignup>()
        .eq("league_no", body.getLeagueNo()).eq("member_no", body.getMemberNo()));
    if (existing != null) {
      existing.setSignupStatus(body.getSignupStatus());
      signupMapper.updateById(existing);
    } else {
      body.setId(null);
      signupMapper.insert(body);
    }
    return ApiResponse.ok();
  }
}
