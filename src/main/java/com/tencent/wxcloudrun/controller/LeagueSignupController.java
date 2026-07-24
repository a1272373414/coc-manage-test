package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.entity.biz.Clan;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.entity.biz.League;
import com.tencent.wxcloudrun.entity.biz.LeagueSignup;
import com.tencent.wxcloudrun.mapper.ClanMapper;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import com.tencent.wxcloudrun.mapper.LeagueMapper;
import com.tencent.wxcloudrun.mapper.LeagueSignupMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/league/signup")
public class LeagueSignupController {

  @Resource
  private LeagueSignupMapper signupMapper;
  @Resource
  private LeagueMapper leagueMapper;
  @Resource
  private ClanMapper clanMapper;
  @Resource
  private ClanMemberMapper clanMemberMapper;

  /** 分页查询报名列表，支持按联赛编号、部落编号、成员名称、成员编号筛选。
   * 同时回填 leagueName/clanName 便于前端展示"名称 + 编号"。 */
  @GetMapping("/page")
  public ApiResponse page(
      @RequestParam(required = false) String leagueNo,
      @RequestParam(required = false) String clanNo,
      @RequestParam(required = false) String memberName,
      @RequestParam(required = false) String memberNo,
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size) {
    Page<LeagueSignup> page = PageResult.page(current, size);
    QueryWrapper<LeagueSignup> qw = new QueryWrapper<>();
    if (leagueNo != null && !leagueNo.trim().isEmpty()) qw.eq("league_no", leagueNo.trim());
    if (clanNo != null && !clanNo.trim().isEmpty()) qw.eq("clan_no", clanNo.trim());
    if (memberName != null && !memberName.trim().isEmpty()) qw.like("member_name", "%" + memberName.trim() + "%");
    if (memberNo != null && !memberNo.trim().isEmpty()) qw.like("member_no", "%" + memberNo.trim() + "%");
    qw.orderByDesc("id");
    signupMapper.selectPage(page, qw);
    fillNames(page.getRecords());
    return ApiResponse.ok(PageResult.of(page));
  }

  /**
   * 批量回填 leagueName 和 clanName：
   * 1) 收集当前页所有非空的 leagueNo / clanNo；
   * 2) 一次 SQL 批量查 league 表和 clan 表（避免 N+1）；
   * 3) 给每条记录回填 leagueName/clanName 非持久化字段（用于列表展示）。
   */
  private void fillNames(List<LeagueSignup> records) {
    if (records == null || records.isEmpty()) return;
    Set<String> leagueNos = records.stream()
        .map(LeagueSignup::getLeagueNo)
        .filter(s -> s != null && !s.isEmpty())
        .collect(Collectors.toSet());
    Set<String> clanNos = records.stream()
        .map(LeagueSignup::getClanNo)
        .filter(s -> s != null && !s.isEmpty())
        .collect(Collectors.toSet());
    Map<String, String> leagueNameMap = new HashMap<>();
    if (!leagueNos.isEmpty()) {
      List<League> leagues = leagueMapper.selectList(
          new QueryWrapper<League>().in("league_no", leagueNos));
      for (League l : leagues) {
        if (l.getLeagueNo() != null) leagueNameMap.put(l.getLeagueNo(), l.getLeagueName());
      }
    }
    Map<String, String> clanNameMap = new HashMap<>();
    if (!clanNos.isEmpty()) {
      List<Clan> clans = clanMapper.selectList(new QueryWrapper<Clan>().in("clan_no", clanNos));
      for (Clan c : clans) {
        if (c.getClanNo() != null) clanNameMap.put(c.getClanNo(), c.getClanName());
      }
    }
    for (LeagueSignup r : records) {
      if (r.getLeagueNo() != null) r.setLeagueName(leagueNameMap.get(r.getLeagueNo()));
      if (r.getClanNo() != null) r.setClanName(clanNameMap.get(r.getClanNo()));
    }
  }

  /** 某联赛的报名名单（受 group_no 隔离约束） */
  @GetMapping("/list")
  public ApiResponse list(@RequestParam String leagueNo) {
    List<LeagueSignup> list = signupMapper.selectList(
        new QueryWrapper<LeagueSignup>().eq("league_no", leagueNo).orderByDesc("id"));
    fillNames(list);
    return ApiResponse.ok(list);
  }

  /** 报名 / 退赛（按 league_no + member_no 幂等更新）。
   * signupTime 由后端自动写入当前时间，前端无需/不许传。 */
  @PostMapping
  public ApiResponse signup(@RequestBody LeagueSignup body) {
    return doSignup(body);
  }

  /** 编辑（前端 createCrud 调用 PUT）*/
  @PutMapping
  public ApiResponse signupUpdate(@RequestBody LeagueSignup body) {
    return doSignup(body);
  }

  /** 删除报名记录（逻辑删除） */
  @DeleteMapping("/{id}")
  public ApiResponse delete(@PathVariable Long id) {
    signupMapper.deleteById(id);
    return ApiResponse.ok();
  }

  private ApiResponse doSignup(LeagueSignup body) {
    if (body.getLeagueNo() == null || body.getMemberNo() == null) {
      return ApiResponse.error("leagueNo 与 memberNo 不能为空");
    }
    body.setSignupTime(LocalDateTime.now());
    LeagueSignup existing = signupMapper.selectOne(new QueryWrapper<LeagueSignup>()
        .eq("league_no", body.getLeagueNo()).eq("member_no", body.getMemberNo()));
    if (existing != null) {
      existing.setSignupStatus(body.getSignupStatus());
      existing.setSignupTime(body.getSignupTime());
      signupMapper.updateById(existing);
    } else {
      body.setId(null);
      signupMapper.insert(body);
    }
    return ApiResponse.ok();
  }

  /**
   * 一键初始化报名数据：
   * 1) 查询指定部落在组状态为"已加入"(member_status=1)的成员
   * 2) 成员默认参战状态 warStatus=1 → signupStatus=2(备选报名)，warStatus=0 → signupStatus=1(未报名)
   * 3) 唯一性判断：(member_name, member_no, league_no, clan_no, group_no)
   *    - 已存在且 signupStatus=3(主动报名) → 跳过
   *    - 已存在且 signupStatus=1/2 → 先删再插
   *    - 不存在 → 直接插入
   */
  @PostMapping("/init")
  public ApiResponse initSignup(
      @RequestParam String leagueNo,
      @RequestParam String clanNo) {
    // 查联赛获取 group_no
    League league = leagueMapper.selectOne(new QueryWrapper<League>().eq("league_no", leagueNo));
    if (league == null) return ApiResponse.error("联赛不存在: " + leagueNo);
    String groupNo = league.getGroupNo();

    // 查询该部落在组状态为已加入的成员
    List<ClanMember> members = clanMemberMapper.selectList(
        new QueryWrapper<ClanMember>().eq("clan_no", clanNo).eq("member_status", 1));

    int inserted = 0, skipped = 0, replaced = 0;
    for (ClanMember m : members) {
      // 映射报名状态
      int signupStatus = (m.getWarStatus() != null && m.getWarStatus() == 1) ? 2 : 1;

      // 查询是否已存在（按 member_name + member_no + league_no + clan_no + group_no 唯一）
      LeagueSignup existing = signupMapper.selectOne(new QueryWrapper<LeagueSignup>()
          .eq("league_no", leagueNo)
          .eq("clan_no", clanNo)
          .eq("member_no", m.getMemberNo())
          .eq("member_name", m.getMemberName()));

      if (existing != null) {
        if (existing.getSignupStatus() != null && existing.getSignupStatus() == 3) {
          // 主动报名 → 跳过
          skipped++;
          continue;
        }
        // 备选报名/未报名 → 先删再插
        signupMapper.deleteById(existing.getId());
        replaced++;
      }

      // 插入新记录
      LeagueSignup signup = new LeagueSignup();
      signup.setLeagueNo(leagueNo);
      signup.setClanNo(clanNo);
      signup.setGroupNo(groupNo);
      signup.setMemberName(m.getMemberName());
      signup.setMemberNo(m.getMemberNo());
      signup.setSignupStatus(signupStatus);
      signup.setSignupTime(LocalDateTime.now());
      signupMapper.insert(signup);
      inserted++;
    }

    Map<String, Object> result = new HashMap<>();
    result.put("total", members.size());
    result.put("inserted", inserted);
    result.put("replaced", replaced);
    result.put("skipped", skipped);
    return ApiResponse.ok(result);
  }
}
