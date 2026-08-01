package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.IgnoreLogin;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.entity.biz.LeagueRecord;
import com.tencent.wxcloudrun.entity.biz.LeagueSignup;
import com.tencent.wxcloudrun.entity.sys.SysConfig;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import com.tencent.wxcloudrun.mapper.LeagueRecordMapper;
import com.tencent.wxcloudrun.mapper.LeagueSignupMapper;
import com.tencent.wxcloudrun.mapper.SysConfigMapper;
import com.tencent.wxcloudrun.util.MemberNoGenerator;
import org.springframework.transaction.annotation.Transactional;
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
	private LeagueSignupMapper leagueSignupMapper;

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
	 * 新增部落成员。同一群组（group_no）内做条件唯一性校验： - 成员编号不为空时，校验成员编号唯一 - 成员编号为空时，校验成员名称唯一
	 *
	 * 普通用户的 group_no 由多租户拦截器自动过滤；超级管理员需从请求体取 groupNo。
	 */
	@Override
	@PostMapping
	@Transactional
	public ApiResponse create(@RequestBody ClanMember body) {
		if (body.getMemberName() == null || body.getMemberName().trim().isEmpty()) {
			return ApiResponse.error("成员名称不能为空");
		}
		String groupNo = resolveGroupNo(body);
		String clanNo = body.getClanNo() == null ? null : body.getClanNo().trim();
		String memberName = body.getMemberName().trim();
		// 成员编号为空时：若存在同名成员（含备用名称）则要求补充编号，否则自动生成 10 位（数字+小写字母）编号
		if (body.getMemberNo() == null || body.getMemberNo().trim().isEmpty()) {
			if (existsSameNameMember(groupNo, clanNo, memberName)) {
				return ApiResponse.error("存在同名成员【" + memberName + "】，请补充编号");
			}
			body.setMemberNo(MemberNoGenerator.generateUniqueMemberNo(clanMemberMapper, groupNo, clanNo));
		}
		else {
			body.setMemberNo(body.getMemberNo().trim());
		}
		ApiResponse dup = checkDuplicate(body, null);
		if (dup != null) {
			return dup;
		}
		body.setId(null);
		clanMemberMapper.insert(body);
		// 同步联赛成员战绩表 / 联赛报名表：将同名且 member_no 为空的关联记录补全为该成员编号
		syncLeagueNoByMemberName(memberName, clanNo, groupNo, body.getMemberNo());
		return ApiResponse.ok(body);
	}

	/** 是否存在同名成员（主名称或任一备用名称），用于新增校验；按 group_no（非空）+ clan_no（非空）范围判断。 */
	private boolean existsSameNameMember(String groupNo, String clanNo, String memberName) {
		if (memberName == null || memberName.trim().isEmpty()) {
			return false;
		}
		String name = memberName.trim();
		QueryWrapper<ClanMember> qw = new QueryWrapper<ClanMember>();
		if (groupNo != null && !groupNo.trim().isEmpty()) {
			qw.eq("group_no", groupNo.trim());
		}
		if (clanNo != null && !clanNo.trim().isEmpty()) {
			qw.eq("clan_no", clanNo.trim());
		}
		qw.and(w -> w.eq("member_name", name).or().eq("backup_name1", name).or().eq("backup_name2", name)
				.or().eq("backup_name3", name).or().eq("backup_name4", name).or().eq("backup_name5", name));
		return clanMemberMapper.selectCount(qw) > 0;
	}

	/**
	 * 历史数据处理：为成员表中 member_no 为空的成员生成唯一编号，并同步更新
	 * 联赛成员战绩表、联赛报名表中关联记录的 member_no（按 名称 + 部落 + 群组 匹配且原 member_no 为空）。
	 */
	@IgnoreLogin
	@GetMapping("/backfillMemberNo")
	@Transactional
	public ApiResponse backfillMemberNo() {
		QueryWrapper<ClanMember> qw = new QueryWrapper<ClanMember>();
		qw.and(w -> w.isNull("member_no").or().eq("member_no", ""));
		List<ClanMember> empties = clanMemberMapper.selectList(qw);
		int updated = 0;
		for (ClanMember m : empties) {
			if (m.getMemberNo() != null && !m.getMemberNo().trim().isEmpty()) {
				continue;
			}
			String newNo = MemberNoGenerator.generateUniqueMemberNo(clanMemberMapper, m.getGroupNo(), m.getClanNo());
			m.setMemberNo(newNo);
			clanMemberMapper.updateById(m);
			syncLeagueNoByMemberName(m.getMemberName(), m.getClanNo(), m.getGroupNo(), newNo);
			updated++;
		}
		Map<String, Object> result = new HashMap<String, Object>(2);
		result.put("updated", updated);
		return ApiResponse.ok(result);
	}

	/** 解析当前操作归属的 group_no：优先取登录上下文，未登录（超管）则取入参。 */
	private String resolveGroupNo(ClanMember body) {
		String groupNo = UserContext.getGroupNo();
		if (groupNo == null || groupNo.isEmpty()) {
			groupNo = body.getGroupNo();
		}
		return groupNo;
	}

	/** 按成员名称（部落 + 群组）将联赛表中 member_no 为空的关联记录补全为 newNo。 */
	private void syncLeagueNoByMemberName(String memberName, String clanNo, String groupNo, String newNo) {
		if (memberName == null || memberName.trim().isEmpty()) {
			return;
		}
		String name = memberName.trim();
		QueryWrapper<LeagueRecord> rqw = new QueryWrapper<LeagueRecord>();
		rqw.eq("clan_no", clanNo);
		if (groupNo != null && !groupNo.trim().isEmpty()) {
			rqw.eq("group_no", groupNo);
		}
		rqw.eq("member_name", name);
		rqw.and(w -> w.isNull("member_no").or().eq("member_no", ""));
		LeagueRecord ru = new LeagueRecord();
		ru.setMemberNo(newNo);
		leagueRecordMapper.update(ru, rqw);

		QueryWrapper<LeagueSignup> sqw = new QueryWrapper<LeagueSignup>();
		sqw.eq("clan_no", clanNo);
		if (groupNo != null && !groupNo.trim().isEmpty()) {
			sqw.eq("group_no", groupNo);
		}
		sqw.eq("member_name", name);
		sqw.and(w -> w.isNull("member_no").or().eq("member_no", ""));
		LeagueSignup su = new LeagueSignup();
		su.setMemberNo(newNo);
		leagueSignupMapper.update(su, sqw);
	}

	/**
	 * 编辑部落成员。复用与新增一致的“编号/名称”条件唯一校验，并排除记录自身。
	 * 编辑后若成员名称或编号发生变化，同步更新联赛成员战绩表、联赛报名表中关联的成员名称/编号；
	 * 且编辑时成员编号一律必填，不可为空。
	 */
	@Override
	@PutMapping
	@Transactional
	public ApiResponse update(@RequestBody ClanMember body) {
		if (body.getId() == null) {
			return ApiResponse.error("id 不能为空");
		}
		if (body.getMemberName() == null || body.getMemberName().trim().isEmpty()) {
			return ApiResponse.error("成员名称不能为空");
		}
		// 取出修改前的成员，用于识别被关联的联赛表记录
		ClanMember old = clanMemberMapper.selectById(body.getId());
		if (old == null) {
			return ApiResponse.error(404, "未找到成员");
		}
		// 编辑时成员编号一律必填，不可为空
		String oldNo = old.getMemberNo();
		boolean hadNo = oldNo != null && !oldNo.trim().isEmpty();
		String newNo = body.getMemberNo() == null ? null : body.getMemberNo().trim();
		if (newNo == null || newNo.isEmpty()) {
			return ApiResponse.error("成员编号不可为空");
		}
		ApiResponse dup = checkDuplicate(body, body.getId());
		if (dup != null) {
			return dup;
		}
		clanMemberMapper.updateById(body);

		// 同步联赛成员战绩表 / 联赛报名表：名称或编号发生变化时才更新
		String newName = body.getMemberName().trim();
		String oldName = old.getMemberName();
		boolean nameChanged = !newName.equals(oldName == null ? "" : oldName);
		boolean noChanged = hadNo && newNo != null && !newNo.equals(oldNo);
		boolean noFilled = !hadNo && newNo != null && !newNo.isEmpty();
		if (nameChanged || noChanged || noFilled) {
			syncLeagueMember(oldName, oldNo, hadNo, old.getClanNo(), old.getGroupNo(), newName, newNo);
		}
		return ApiResponse.ok(body);
	}

	/**
	 * 将成员名称/编号的变更同步到联赛成员战绩表与联赛报名表。
	 * 关联定位：同一 clan_no（及 group_no）下，原编号不为空时按 member_no 匹配，原编号为空时按 member_name 匹配。
	 * 仅更新发生变化的字段：名称变化则更新 member_name；编号变化（或原本为空现被填写）则更新 member_no。
	 */
	private void syncLeagueMember(String oldName, String oldNo, boolean hadNo, String oldClanNo, String oldGroupNo,
			String newName, String newNo) {
		// 联赛成员战绩表
		QueryWrapper<LeagueRecord> rqw = new QueryWrapper<LeagueRecord>();
		rqw.eq("clan_no", oldClanNo);
		if (oldGroupNo != null && !oldGroupNo.trim().isEmpty()) {
			rqw.eq("group_no", oldGroupNo);
		}
		if (hadNo) {
			rqw.eq("member_no", oldNo);
		}
		else {
			rqw.eq("member_name", oldName);
		}
		LeagueRecord recordUpd = new LeagueRecord();
		recordUpd.setMemberName(newName);
		if (newNo != null && !newNo.isEmpty()) {
			recordUpd.setMemberNo(newNo);
		}
		leagueRecordMapper.update(recordUpd, rqw);

		// 联赛报名表
		QueryWrapper<LeagueSignup> sqw = new QueryWrapper<LeagueSignup>();
		sqw.eq("clan_no", oldClanNo);
		if (oldGroupNo != null && !oldGroupNo.trim().isEmpty()) {
			sqw.eq("group_no", oldGroupNo);
		}
		if (hadNo) {
			sqw.eq("member_no", oldNo);
		}
		else {
			sqw.eq("member_name", oldName);
		}
		LeagueSignup signupUpd = new LeagueSignup();
		signupUpd.setMemberName(newName);
		if (newNo != null && !newNo.isEmpty()) {
			signupUpd.setMemberNo(newNo);
		}
		leagueSignupMapper.update(signupUpd, sqw);
	}

	/**
	 * 部落成员分页查询：支持关键字（名称/编号/部落编号）、各字段精确过滤， 以及按 大本等级/匹配值/战斗力 排序（sortField +
	 * sortOrder，列名白名单防注入）。 保持与基类一致的签名以正确覆盖 /page，额外参数从 HttpServletRequest 读取。
	 */
	@Override
	@GetMapping("/page")
	public ApiResponse page(@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size) {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
			.getRequest();
		String memberName = trimToNull(request.getParameter("memberName"));
		String memberNo = trimToNull(request.getParameter("memberNo"));
		String clanNo = trimToNull(request.getParameter("clanNo"));
		Integer warStatus = toIntParam(request.getParameter("warStatus"));
		Integer memberStatus = toIntParam(request.getParameter("memberStatus"));
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
		if (memberName != null)
			qw.like("member_name", memberName);
		if (memberNo != null)
			qw.like("member_no", memberNo);
		if (warStatus != null)
			qw.eq("war_status", warStatus);
		if (memberStatus != null)
			qw.eq("member_status", memberStatus);
		if (clanNo != null)
			qw.eq("clan_no", clanNo);
		if (thLevel != null)
			qw.eq("th_level", thLevel);
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
		if (s == null)
			return null;
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private Integer toIntParam(String s) {
		if (s == null)
			return null;
		String t = s.trim();
		if (t.isEmpty())
			return null;
		try {
			return Integer.valueOf(t);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/** 排序字段白名单：前端 prop 名 → 数据库列名，防止 order by 注入。 */
	private String sortColumn(String field) {
		switch (field) {
			case "memberName":
				return "member_name";
			case "thLevel":
				return "th_level";
			case "matchValue":
				return "match_value";
			case "combatPower":
				return "combat_power";
			default:
				return null;
		}
	}

	/**
	 * 条件唯一校验（同一群组 group_no 内）： - 成员编号不为空 → 按 (group_no, member_no) 查重 - 成员编号为空 → 按
	 * (group_no, member_name) 查重 excludeId 不为空时排除该记录本身（编辑场景）。无群组上下文时返回 null（跳过校验）。
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
		}
		else {
			qw.eq("member_name", body.getMemberName().trim());
		}
		if (excludeId != null) {
			qw.ne("id", excludeId);
		}
		Long count = clanMemberMapper.selectCount(qw);
		if (count != null && count > 0) {
			return ApiResponse.error(hasNo ? "同一群组下已存在相同成员编号的成员" : "同一群组下已存在相同成员名称的成员");
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
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	/**
	 * 获取战斗力计算默认配置（从系统配置表读取，用于弹窗预填）
	 */
	@GetMapping("/combatPower/config")
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
	 * 一键计算指定部落所有成员的战斗力，并保存到数据库。 计算公式： 进攻概率=实际进攻总次数/应该进攻总次数 参赛概率=应该进攻次数>0的战绩条数/参赛次数(战绩总条数)
	 * 三星概率=总星数/总应该进攻次数 防御概率=大本等级/配置最高大本等级*50% + 匹配值/配置最高匹配值*50%
	 * 战斗力=进攻概率*进攻得分+参赛概率*参赛得分+三星概率*三星得分+防御概率*防御得分（取整）
	 */
	@PostMapping("/combatPower/calculate")
	public ApiResponse calcCombatPower(@RequestBody CombatPowerCalcRequest req) {
		if (req.getClanNo() == null || req.getClanNo().trim().isEmpty()) {
			return ApiResponse.error("请选择部落");
		}
		if (req.getAttackScore() == null || req.getParticipateScore() == null || req.getThreeStarScore() == null
				|| req.getDefenseScore() == null) {
			return ApiResponse.error("请填写完整的得分分配");
		}
		String clanNo = req.getClanNo().trim();
		String groupNo = UserContext.getGroupNo();
		if (groupNo == null || groupNo.isEmpty()) {
			return ApiResponse.error("群组编号为空");
		}

		// 部落成员
		QueryWrapper<ClanMember> mqw = new QueryWrapper<ClanMember>();
		mqw.eq("clan_no", clanNo);
		mqw.eq("group_no", groupNo);
		List<ClanMember> members = clanMemberMapper.selectList(mqw);
		if (members.isEmpty()) {
			return ApiResponse.error("该部落下没有成员数据");
		}

		// 联赛成员战绩，不区分部落
		QueryWrapper<LeagueRecord> rqw = new QueryWrapper<LeagueRecord>();
		// rqw.eq("clan_no", clanNo);
		rqw.eq("group_no", groupNo);
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

			double combat = attackProb * attackScore + participateProb * participateScore
					+ threeStarProb * threeStarScore + defenseProb * defenseScore;
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

	/**
	 * 合并成员：将 mergeId 对应的“被合并成员”并入 mainId 对应的“主数据”。
	 * 1) 被合并成员的名称（不含备用名称）写入主数据第一个为空的备用名称字段；
	 * 2) 被合并成员关联的联赛成员战绩表、联赛报名表记录改为关联主数据；
	 * 3) 删除被合并成员。
	 * 仅允许合并同一群组（group_no）与同一部落（clan_no）下的成员。
	 */
	@PostMapping("/merge")
	@Transactional
	public ApiResponse merge(@RequestBody MemberMergeRequest req) {
		if (req.getMainId() == null || req.getMergeId() == null) {
			return ApiResponse.error("参数缺失");
		}
		if (req.getMainId().equals(req.getMergeId())) {
			return ApiResponse.error("不能将成员合并到自身");
		}
		ClanMember main = clanMemberMapper.selectById(req.getMainId());
		ClanMember merge = clanMemberMapper.selectById(req.getMergeId());
		if (main == null) {
			return ApiResponse.error("未找到主数据成员");
		}
		if (merge == null) {
			return ApiResponse.error("未找到被合并成员");
		}
		if (!hasEmptyBackupName(main)) {
			return ApiResponse.error("主数据备用名称字段已满，无法合并");
		}
		if (!sameGroupAndClan(main, merge)) {
			return ApiResponse.error("仅可合并同一部落下的成员");
		}
		// 1) 被合并成员名称写入主数据空闲的备用名称字段
		fillBackupName(main, merge.getMemberName());
		clanMemberMapper.updateById(main);
		// 2) 关联联赛两表转给主数据
		reassignLeague(main, merge);
		// 3) 删除被合并成员
		clanMemberMapper.deleteById(merge.getId());
		return ApiResponse.ok();
	}

	/** 主数据与被合并数据是否同群组且同部落。 */
	private boolean sameGroupAndClan(ClanMember a, ClanMember b) {
		String ac = a.getClanNo() == null ? "" : a.getClanNo();
		String bc = b.getClanNo() == null ? "" : b.getClanNo();
		if (!ac.equals(bc)) {
			return false;
		}
		String ag = a.getGroupNo() == null ? "" : a.getGroupNo();
		String bg = b.getGroupNo() == null ? "" : b.getGroupNo();
		if (!ag.equals(bg)) {
			return false;
		}
		return true;
	}

	/** 将名称写入主数据第一个为空的备用名称字段（backup_name1~5）。 */
	private void fillBackupName(ClanMember main, String name) {
		if (name == null || name.trim().isEmpty()) {
			return;
		}
		String n = name.trim();
		if (isBlank(main.getBackupName1())) {
			main.setBackupName1(n);
		}
		else if (isBlank(main.getBackupName2())) {
			main.setBackupName2(n);
		}
		else if (isBlank(main.getBackupName3())) {
			main.setBackupName3(n);
		}
		else if (isBlank(main.getBackupName4())) {
			main.setBackupName4(n);
		}
		else if (isBlank(main.getBackupName5())) {
			main.setBackupName5(n);
		}
	}

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	/** 主数据是否还有为空的备用名称字段（backup_name1~5）。 */
	private boolean hasEmptyBackupName(ClanMember m) {
		return isBlank(m.getBackupName1()) || isBlank(m.getBackupName2())
			|| isBlank(m.getBackupName3()) || isBlank(m.getBackupName4())
			|| isBlank(m.getBackupName5());
	}

	/** 将被合并成员关联的联赛两表记录改为关联主数据（按成员名称或编号匹配）。 */
	private void reassignLeague(ClanMember main, ClanMember merge) {
		String clanNo = main.getClanNo();
		String groupNo = main.getGroupNo();
		String mainName = main.getMemberName();
		String mainNo = main.getMemberNo();
		String mergeName = merge.getMemberName();
		String mergeNo = merge.getMemberNo();

		// 联赛成员战绩表
		LeagueRecord ru = new LeagueRecord();
		ru.setMemberName(mainName);
		if (mainNo != null && !mainNo.trim().isEmpty()) {
			ru.setMemberNo(mainNo);
		}
		QueryWrapper<LeagueRecord> rqw = new QueryWrapper<LeagueRecord>();
		rqw.eq("clan_no", clanNo);
		if (groupNo != null && !groupNo.trim().isEmpty()) {
			rqw.eq("group_no", groupNo);
		}
		rqw.and(w -> {
			w.eq("member_name", mergeName);
			if (mergeNo != null && !mergeNo.trim().isEmpty()) {
				w.or().eq("member_no", mergeNo);
			}
		});
		leagueRecordMapper.update(ru, rqw);

		// 联赛报名表
		LeagueSignup su = new LeagueSignup();
		su.setMemberName(mainName);
		if (mainNo != null && !mainNo.trim().isEmpty()) {
			su.setMemberNo(mainNo);
		}
		QueryWrapper<LeagueSignup> sqw = new QueryWrapper<LeagueSignup>();
		sqw.eq("clan_no", clanNo);
		if (groupNo != null && !groupNo.trim().isEmpty()) {
			sqw.eq("group_no", groupNo);
		}
		sqw.and(w -> {
			w.eq("member_name", mergeName);
			if (mergeNo != null && !mergeNo.trim().isEmpty()) {
				w.or().eq("member_no", mergeNo);
			}
		});
		leagueSignupMapper.update(su, sqw);
	}

	/** 合并成员请求体。 */
	private static class MemberMergeRequest {

		private Long mainId;

		private Long mergeId;

		public Long getMainId() {
			return mainId;
		}

		public void setMainId(Long mainId) {
			this.mainId = mainId;
		}

		public Long getMergeId() {
			return mergeId;
		}

		public void setMergeId(Long mergeId) {
			this.mergeId = mergeId;
		}

	}

}
