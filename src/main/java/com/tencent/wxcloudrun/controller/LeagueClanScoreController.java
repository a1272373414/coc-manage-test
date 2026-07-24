package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.entity.biz.Clan;
import com.tencent.wxcloudrun.entity.biz.League;
import com.tencent.wxcloudrun.entity.biz.LeagueClanScore;
import com.tencent.wxcloudrun.mapper.ClanMapper;
import com.tencent.wxcloudrun.mapper.LeagueClanScoreMapper;
import com.tencent.wxcloudrun.mapper.LeagueMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 联赛部落成绩 CRUD（继承 BaseCrudController 通用分页/增删改查）。
 */
@RestController
@RequestMapping("/api/league/score")
public class LeagueClanScoreController extends BaseCrudController<LeagueClanScore> {

  @Resource
  private LeagueClanScoreMapper leagueClanScoreMapper;

  @Resource
  private LeagueMapper leagueMapper;

  @Resource
  private ClanMapper clanMapper;

  @Override
  protected BaseMapper<LeagueClanScore> mapper() {
    return leagueClanScoreMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("league_no", "clan_no");
  }

  /**
   * 重写分页：支持按 leagueNo、clanNo、promoteStatus 字段精确筛选。
   * 前端 createCrud 的搜索栏会传 filters.leagueNo / filters.clanNo / filters.promoteStatus。
   */
  @Override
  @GetMapping("/page")
  public ApiResponse page(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size) {
    Page<LeagueClanScore> page = PageResult.page(current, size);
    QueryWrapper<LeagueClanScore> qw = new QueryWrapper<>();
    // 从请求参数中读取字段级筛选（前端 createCrud 的 filters 会作为 query param 传入）
    HttpServletRequest req = ((org.springframework.web.context.request.ServletRequestAttributes)
        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest();
    String leagueNo = req.getParameter("leagueNo");
    String clanNo = req.getParameter("clanNo");
    String promoteStatus = req.getParameter("promoteStatus");
    if (leagueNo != null && !leagueNo.trim().isEmpty()) qw.eq("league_no", leagueNo.trim());
    if (clanNo != null && !clanNo.trim().isEmpty()) qw.eq("clan_no", clanNo.trim());
    if (promoteStatus != null && !promoteStatus.trim().isEmpty()) qw.eq("promote_status", promoteStatus.trim());
    // keyword 模糊搜索
    List<String> fields = keywordFields();
    if (keyword != null && !keyword.trim().isEmpty() && !fields.isEmpty()) {
      String kw = keyword.trim();
      qw.and(w -> {
        boolean first = true;
        for (String field : fields) {
          if (!first) w.or();
          w.like(field, kw);
          first = false;
        }
      });
    }
    qw.orderByDesc("id");
    mapper().selectPage(page, qw);

    // 批量填充联赛名称、部落名称供前端展示
    List<LeagueClanScore> records = page.getRecords();
    if (!records.isEmpty()) {
      Set<String> leagueNos = records.stream()
          .map(LeagueClanScore::getLeagueNo)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
      Set<String> clanNos = records.stream()
          .map(LeagueClanScore::getClanNo)
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

      for (LeagueClanScore record : records) {
        String ln = record.getLeagueNo();
        String cn = record.getClanNo();
        record.setLeagueName(leagueNameMap.getOrDefault(ln, ln));
        record.setClanName(clanNameMap.getOrDefault(cn, cn));
      }
    }

    return ApiResponse.ok(PageResult.of(page));
  }
}
