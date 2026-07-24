package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.Clan;
import com.tencent.wxcloudrun.entity.biz.League;
import com.tencent.wxcloudrun.entity.biz.LeagueClanScore;
import com.tencent.wxcloudrun.mapper.ClanMapper;
import com.tencent.wxcloudrun.mapper.LeagueClanScoreMapper;
import com.tencent.wxcloudrun.mapper.LeagueMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  @Resource
  private ClanMapper clanMapper;
  @Resource
  private LeagueClanScoreMapper leagueClanScoreMapper;

  @Override
  protected BaseMapper<League> mapper() {
    return leagueMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("league_name", "league_no");
  }

  /**
   * 新增联赛：
   * 1) 校验联赛编号唯一性
   * 2) 插入联赛记录
   * 3) 查询当前用户所属群组下的所有部落，为每个部落生成一条联赛部落成绩记录
   */
  @Override
  @PostMapping
  public ApiResponse create(@RequestBody League body) {
    body.setId(null);
    // 确保 group_no 有值（优先用前端传入，其次取当前登录用户）
    String groupNo = body.getGroupNo();
    if (groupNo == null || groupNo.isEmpty()) {
      groupNo = UserContext.getGroupNo();
    }
    if (groupNo == null || groupNo.isEmpty()) {
      return ApiResponse.error("无法确定所属群组，请先登录或联系管理员");
    }
    body.setGroupNo(groupNo);
    // 按 league_no + group_no 校验唯一性（@TableLogic 自动过滤 deleted=0）
    if (body.getLeagueNo() != null && !body.getLeagueNo().isEmpty()) {
      League existing = leagueMapper.selectOne(
          new QueryWrapper<League>().eq("league_no", body.getLeagueNo()).eq("group_no", groupNo));
      if (existing != null) return ApiResponse.error(409, "该群组下联赛编号已存在：" + body.getLeagueNo());
    }
    mapper().insert(body);

    // 查询该群组下所有部落，为每个部落生成联赛部落成绩记录
    List<Clan> clans = clanMapper.selectList(new QueryWrapper<Clan>().eq("group_no", groupNo));
    for (Clan c : clans) {
      LeagueClanScore score = new LeagueClanScore();
      score.setLeagueNo(body.getLeagueNo());
      score.setClanNo(c.getClanNo());
      score.setGroupNo(groupNo);
      score.setPromoteStatus(0);
      leagueClanScoreMapper.insert(score);
    }

    return ApiResponse.ok(body);
  }

  /** 更新时按 league_no + group_no 校验唯一性（排除自身）。 */
  @Override
  @PutMapping
  public ApiResponse update(@RequestBody League body) {
    if (body.getId() == null) return ApiResponse.error("id 不能为空");
    if (body.getLeagueNo() != null && !body.getLeagueNo().isEmpty()) {
      String groupNo = body.getGroupNo();
      if (groupNo == null || groupNo.isEmpty()) {
        League self = leagueMapper.selectById(body.getId());
        if (self != null) groupNo = self.getGroupNo();
      }
      QueryWrapper<League> qw = new QueryWrapper<League>()
          .eq("league_no", body.getLeagueNo()).ne("id", body.getId());
      if (groupNo != null && !groupNo.isEmpty()) {
        qw.eq("group_no", groupNo);
      }
      League existing = leagueMapper.selectOne(qw);
      if (existing != null) return ApiResponse.error(409, "该群组下联赛编号已被其他联赛使用：" + body.getLeagueNo());
    }
    mapper().updateById(body);
    return ApiResponse.ok(body);
  }
}
