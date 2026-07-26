package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.entity.biz.LeagueRecord;
import com.tencent.wxcloudrun.entity.sys.SysConfig;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import com.tencent.wxcloudrun.mapper.LeagueRecordMapper;
import com.tencent.wxcloudrun.mapper.SysConfigMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clan/member")
public class ClanMemberController extends BaseCrudController<ClanMember> {

  @Resource
  private ClanMemberMapper clanMemberMapper;

  @Resource
  private LeagueRecordMapper leagueRecordMapper;

  @Resource
  private SysConfigMapper sysConfigMapper;

  @Override
  protected BaseMapper<ClanMember> mapper() {
    return clanMemberMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("member_name", "member_no", "clan_no");
  }

  /**
   * 新增部落成员。同一群组（group_no）内做条件唯一性校验：
   * - 成员编号不为空时，校验成员编号唯一
   * - 成员编号为空时，校验成员名称唯一
   *
   * 普通用户的 group_no 由多租户拦截器自动过滤；超级管理员需从请求体取 groupNo。
   */
  @Override
  @PostMapping
  public ApiResponse create(@RequestBody ClanMember body) {
    if (body.getMemberName() == null || body.getMemberName().trim().isEmpty()) {
      return ApiResponse.error("成员名称不能为空");
    }
    ApiResponse dup = checkDuplicate(body, null);
    if (dup != null) {
      return dup;
    }
    body.setId(null);
    clanMemberMapper.insert(body);
    return ApiResponse.ok(body);
  }

  /**
   * 编辑部落成员。复用与新增一致的“编号/名称”条件唯一校验，并排除记录自身。
   */
  @Override
  @PutMapping
  public ApiResponse update(@RequestBody ClanMember body) {
    if (body.getId() == null) {
      return ApiResponse.error("id 不能为空");
    }
    if (body.getMemberName() == null || body.getMemberName().trim().isEmpty()) {
      return ApiResponse.error("成员名称不能为空");
    }
    ApiResponse dup = checkDuplicate(body, body.getId());
    if (dup != null) {
      return dup;
    }
    clanMemberMapper.updateById(body);
    return ApiResponse.ok(body);
  }

  /**
   * 部落成员分页查询：支持关键字（名称/编号/部落编号）、各字段精确过滤，
   * 以及按 大本等级/匹配值/战斗力 排序（sortField + sortOrder，列名白名单防注入）。
   * 保持与基类一致的签名以正确覆盖 /page，额外参数从 HttpServletRequest 读取。
   */
  @Override
  @GetMapping("/page")
  public ApiResponse page(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size) {
    HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    String memberName = trimToNull(request.getParameter("memberName"));
    String memberNo = trimToNull(request.getParameter("memberNo"));
    String clanNo = trimToNull(request.getParameter("clanNo"));
    Integer warStatus = toIntParam(request.getParameter("warStatus"));
    Integer thLevel = toIntParam(request.getParameter("thLevel"));
    String sortField = trimToNull(request.getParameter("sortField"));
    String sortOrder = trimToNull(request.getParameter("sortOrder"));

    QueryWrapper<ClanMember> qw = new QueryWrapper<>();
    // 关键字模糊匹配 member_name / member_no / clan_no
    List<String> fields = keywordFields();
    if (keyword != null && !keyword.trim().isEmpty() && !fields.isEmpty()) {
      String kw = keyword.trim();
      qw.and(w -> {
        boolean first = true;
        for (String field : fields) {
          if (!first) {
            w.or();
          }
          w.like(field, kw);
          first = false;
        }
      });
    }
    // 各条件精确/模糊过滤
    if (memberName != null) qw.like("member_name", memberName);
    if (memberNo != null) qw.like("member_no", memberNo);
    if (warStatus != null) qw.eq("war_status", warStatus);
    if (clanNo != null) qw.eq("clan_no", clanNo);
    if (thLevel != null) qw.eq("th_level", thLevel);
    // 排序：仅允许白名单列，避免 SQL 注入；默认按 id 降序兜底保证分页稳定
    if (sortField != null) {
      String column = sortColumn(sortField);
      if (column != null) {
        boolean asc = !"desc".equalsIgnoreCase(sortOrder == null ? "" : sortOrder);
        qw.orderBy(true, asc, column);
      }
    }
    qw.orderByDesc("id");
    Page<ClanMember> page = PageResult.page(current, size);
    clanMemberMapper.selectPage(page, qw);
    return ApiResponse.ok(PageResult.of(page));
  }

  private String trimToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private Integer toIntParam(String s) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    try {
      return Integer.valueOf(t);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** 排序字段白名单：前端 prop 名 → 数据库列名，防止 order by 注入。 */
  private String sortColumn(String field) {
    switch (field) {
      case "thLevel": return "th_level";
      case "matchValue": return "match_value";
      case "combatPower": return "combat_power";
      default: return null;
    }
  }

  /**
   * 条件唯一校验（同一群组 group_no 内）：
   * - 成员编号不为空 → 按 (group_no, member_no) 查重
   * - 成员编号为空   → 按 (group_no, member_name) 查重
   * excludeId 不为空时排除该记录本身（编辑场景）。无群组上下文时返回 null（跳过校验）。
   */
  private ApiResponse checkDuplicate(ClanMember body, Long excludeId) {
    String groupNo = UserContext.getGroupNo();
    if (groupNo == null || groupNo.isEmpty()) {
      groupNo = body.getGroupNo();
    }
    if (groupNo == null || groupNo.isEmpty()) {
      return null;
    }
    boolean hasNo = body.getMemberNo() != null && !body.getMemberNo().trim().isEmpty();
    QueryWrapper<ClanMember> qw = new QueryWrapper<>();
    qw.eq("group_no", groupNo);
    if (body.getClanNo() != null && !body.getClanNo().trim().isEmpty()) {
      qw.eq("clan_no", body.getClanNo().trim());
    }
    if (hasNo) {
      qw.eq("member_no", body.getMemberNo().trim());
    } else {
      qw.eq("member_name", body.getMemberName().trim());
    }
    if (excludeId != null) {
      qw.ne("id", excludeId);
    }
    Long count = clanMemberMapper.selectCount(qw);
    if (count != null && count > 0) {
      return ApiResponse.error(hasNo
          ? "同一群组下已存在相同成员编号的成员"
          : "同一群组下已存在相同成员名称的成员");
    }
    return null;
  }

  /**
   * 读取字符串类型的系统配置值（系统配置表为全局配置，无群组隔离）
   */
  private String getConfigValue(String configName, String defaultValue) {
    QueryWrapper<SysConfig> qw = new QueryWrapper<SysConfig>();
    qw.eq("config_name", configName);
    SysConfig config = sysConfigMapper.selectOne(qw);
    return config == null ? defaultValue : config.getConfigValue();
  }

  /**
   * 读取整型系统配置值
   */
  private int getIntConfig(String configName, int defaultValue) {
    String v = getConfigValue(configName, null);
    if (v == null || v.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(v.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * 获取战斗力计算默认配置（从系统配置表读取，用于弹窗预填）
   */
  @GetMapping("/combat-power/config")
  public ApiResponse combatPowerConfig() {
    Map<String, Object> cfg = new HashMap<String, Object>(8);
    cfg.put("attackScore", getIntConfig("attack_score", 2500));
    cfg.put("participateScore", getIntConfig("participate_score", 2500));
    cfg.put("threeStarScore", getIntConfig("three_star_score", 2500));
    cfg.put("defenseScore", getIntConfig("defense_score", 2500));
    cfg.put("maxThLevel", getIntConfig("max_th_level", 17));
    cfg.put("maxMatchValue", getIntConfig("max_match_value", 0));
    return ApiResponse.ok(cfg);
  }

  /**
   * 一键计算指定部落所有成员的战斗力，并保存到数据库。
   * 计算公式：
   * 进攻概率=实际进攻总次数/应该进攻总次数
   * 参赛概率=应该进攻次数>0的战绩条数/参赛次数(战绩总条数)
   * 三星概率=总星数/总应该进攻次数
   * 防御概率=大本等级/配置最高大本等级*50% + 匹配值/配置最高匹配值*50%
   * 战斗力=进攻概率*进攻得分+参赛概率*参赛得分+三星概率*三星得分+防御概率*防御得分（取整）
   */
  @PostMapping("/combat-power/calculate")
  public ApiResponse calcCombatPower(@RequestBody CombatPowerCalcRequest req) {
    if (req.getClanNo() == null || req.getClanNo().trim().isEmpty()) {
      return ApiResponse.error("请选择部落");
    }
    if (req.getAttackScore() == null || req.getParticipateScore() == null
        || req.getThreeStarScore() == null || req.getDefenseScore() == null) {
      return ApiResponse.error("请填写完整的得分分配");
    }
    String clanNo = req.getClanNo().trim();
    String groupNo = UserContext.getGroupNo();

    // 部落成员
    QueryWrapper<ClanMember> mqw = new QueryWrapper<ClanMember>();
    mqw.eq("clan_no", clanNo);
    if (groupNo != null && !groupNo.isEmpty()) {
      mqw.eq("group_no", groupNo);
    }
    List<ClanMember> members = clanMemberMapper.selectList(mqw);
    if (members.isEmpty()) {
      return ApiResponse.error("该部落下没有成员数据");
    }

    // 联赛成员战绩
    QueryWrapper<LeagueRecord> rqw = new QueryWrapper<LeagueRecord>();
    rqw.eq("clan_no", clanNo);
    if (groupNo != null && !groupNo.isEmpty()) {
      rqw.eq("group_no", groupNo);
    }
    List<LeagueRecord> records = leagueRecordMapper.selectList(rqw);

    // 按成员名聚合战绩
    Map<String, RecordAgg> aggMap = new HashMap<String, RecordAgg>();
    for (LeagueRecord r : records) {
      String name = r.getMemberName();
      if (name == null || name.trim().isEmpty()) {
        continue;
      }
      RecordAgg a = aggMap.get(name);
      if (a == null) {
        a = new RecordAgg();
        aggMap.put(name, a);
      }
      a.actual += (r.getActualAttacks() == null ? 0 : r.getActualAttacks());
      a.required += (r.getRequiredAttacks() == null ? 0 : r.getRequiredAttacks());
      a.winStars += (r.getWinStars() == null ? 0 : r.getWinStars());
      a.totalRecords++;
      if (r.getRequiredAttacks() != null && r.getRequiredAttacks() > 0) {
        a.participatedRecords++;
      }
    }

    int maxThLevel = getIntConfig("max_th_level", 18);
    int maxMatchValue = getIntConfig("max_match_value", 900);
    double attackScore = req.getAttackScore().doubleValue();
    double participateScore = req.getParticipateScore().doubleValue();
    double threeStarScore = req.getThreeStarScore().doubleValue();
    double defenseScore = req.getDefenseScore().doubleValue();

    int updated = 0;
    for (ClanMember m : members) {
      RecordAgg a = aggMap.get(m.getMemberName());
      double attackProb = 0d;
      double participateProb = 0d;
      double threeStarProb = 0d;
      if (a != null) {
        attackProb = a.required > 0 ? (double) a.actual / a.required : 0d;
        participateProb = a.totalRecords > 0 ? (double) a.participatedRecords / a.totalRecords : 0d;
        threeStarProb = a.required > 0 ? (double) a.winStars / (a.required * 3) : 0d;
      }
      int th = m.getThLevel() == null ? 0 : m.getThLevel();
      int mv = m.getMatchValue() == null ? 0 : m.getMatchValue();
      double thPart = maxThLevel > 0 ? (double) th / maxThLevel : 0d;
      double mvPart = maxMatchValue > 0 ? (double) mv / maxMatchValue : 0d;
      double defenseProb = thPart * 0.5 + mvPart * 0.5;

      double combat = attackProb * attackScore
          + participateProb * participateScore
          + threeStarProb * threeStarScore
          + defenseProb * defenseScore;
      m.setCombatPower((int) Math.round(combat));
      clanMemberMapper.updateById(m);
      updated++;
    }

    Map<String, Object> result = new HashMap<String, Object>(2);
    result.put("updated", updated);
    return ApiResponse.ok(result);
  }

  /**
   * 联赛成员战绩聚合数据
   */
  private static class RecordAgg {
    int actual = 0;
    int required = 0;
    int winStars = 0;
    int totalRecords = 0;
    int participatedRecords = 0;
  }
}
