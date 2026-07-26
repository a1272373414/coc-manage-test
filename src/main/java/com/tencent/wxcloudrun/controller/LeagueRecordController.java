package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.entity.biz.Clan;
import com.tencent.wxcloudrun.entity.biz.League;
import com.tencent.wxcloudrun.entity.biz.LeagueRecord;
import com.tencent.wxcloudrun.mapper.ClanMapper;
import com.tencent.wxcloudrun.mapper.LeagueMapper;
import com.tencent.wxcloudrun.mapper.LeagueRecordMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/league/record")
public class LeagueRecordController extends BaseCrudController<LeagueRecord> {

  @Resource
  private LeagueRecordMapper leagueRecordMapper;

  @Resource
  private LeagueMapper leagueMapper;

  @Resource
  private ClanMapper clanMapper;

  @Override
  protected BaseMapper<LeagueRecord> mapper() {
    return leagueRecordMapper;
  }

  /**
   * 重写分页：支持按联赛、部落精确筛选，并返回联赛/部落名称用于展示。
   */
  @SuppressWarnings("null")
  @Override
  @GetMapping("/page")
  public ApiResponse page(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size) {
    Page<LeagueRecord> page = PageResult.page(current, size);
    QueryWrapper<LeagueRecord> qw = new QueryWrapper<>();

    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    HttpServletRequest req = attrs != null ? attrs.getRequest() : null;
    String leagueNo = req != null ? req.getParameter("leagueNo") : null;
    String clanNo = req != null ? req.getParameter("clanNo") : null;

    if (leagueNo != null && !leagueNo.trim().isEmpty()) {
      qw.eq("league_no", leagueNo.trim());
    }
    if (clanNo != null && !clanNo.trim().isEmpty()) {
      qw.eq("clan_no", clanNo.trim());
    }
    if (keyword != null && !keyword.trim().isEmpty()) {
      String kw = keyword.trim();
      qw.and(w -> w.like("member_name", kw).or().like("member_no", kw));
    }
    // 排序：联赛编号大的排前面，排名数字小的排前面，id 降序兜底保证分页稳定
    qw.orderByDesc("league_no");
    qw.orderByAsc("member_rank");
    qw.orderByDesc("id");
    mapper().selectPage(page, qw);

    // 批量填充联赛名称、部落名称
    List<LeagueRecord> records = page.getRecords();
    if (!records.isEmpty()) {
      Set<String> leagueNos = records.stream()
          .map(LeagueRecord::getLeagueNo)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
      Set<String> clanNos = records.stream()
          .map(LeagueRecord::getClanNo)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());

      Map<String, String> leagueNameMap = new HashMap<>();
      if (!leagueNos.isEmpty()) {
        List<League> leagues = leagueMapper.selectList(
            new QueryWrapper<League>().in("league_no", leagueNos));
        for (League league : leagues) {
          leagueNameMap.put(league.getLeagueNo(), league.getLeagueName());
        }
      }

      Map<String, String> clanNameMap = new HashMap<>();
      if (!clanNos.isEmpty()) {
        List<Clan> clans = clanMapper.selectList(
            new QueryWrapper<Clan>().in("clan_no", clanNos));
        for (Clan clan : clans) {
          clanNameMap.put(clan.getClanNo(), clan.getClanName());
        }
      }

      for (LeagueRecord record : records) {
        String ln = record.getLeagueNo();
        String cn = record.getClanNo();
        record.setLeagueName(leagueNameMap.getOrDefault(ln, ln));
        record.setClanName(clanNameMap.getOrDefault(cn, cn));
      }
    }

    return ApiResponse.ok(PageResult.of(page));
  }
}
