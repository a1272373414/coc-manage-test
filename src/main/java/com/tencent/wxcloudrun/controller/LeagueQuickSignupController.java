package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.entity.biz.Clan;
import com.tencent.wxcloudrun.entity.biz.ClanGroup;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.entity.biz.League;
import com.tencent.wxcloudrun.entity.biz.LeagueClanScore;
import com.tencent.wxcloudrun.entity.biz.LeagueRecord;
import com.tencent.wxcloudrun.entity.biz.LeagueSignup;
import com.tencent.wxcloudrun.entity.dict.DictItem;
import com.tencent.wxcloudrun.mapper.ClanGroupMapper;
import com.tencent.wxcloudrun.mapper.ClanMapper;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import com.tencent.wxcloudrun.mapper.LeagueClanScoreMapper;
import com.tencent.wxcloudrun.mapper.LeagueMapper;
import com.tencent.wxcloudrun.mapper.LeagueRecordMapper;
import com.tencent.wxcloudrun.mapper.LeagueSignupMapper;
import com.tencent.wxcloudrun.mapper.DictItemMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 联赛快速报名（公开接口，无需登录）。
 *
 * 业务模型：群组(group_no) → 联赛(league_no) + 部落(clan_no) → 报名记录(league_signup)。
 * 通过群组编号隔离数据，前端入口路径形如：/league/quickSignup?groupNo=xxx。
 *
 * 为降低外部风险： 1) 所有读接口均校验 groupNo 必传且群组存在； 2) 报名接口要求 memberName + clanNo 必填，自动反查
 * leagueNo（取该群组最近创建的联赛）； 3) 报名状态默认为 3（主动报名），与既有 LeagueSignupController.doSignup 行为一致。
 */
@RestController
@RequestMapping("/api/quick")
public class LeagueQuickSignupController {

	@Resource
	private ClanGroupMapper clanGroupMapper;

	@Resource
	private ClanMapper clanMapper;

	@Resource
	private LeagueMapper leagueMapper;

	@Resource
	private LeagueSignupMapper leagueSignupMapper;

	@Resource
	private ClanMemberMapper clanMemberMapper;

	@Resource
	private LeagueRecordMapper leagueRecordMapper;

	@Resource
	private LeagueClanScoreMapper leagueClanScoreMapper;

	@Resource
	private DictItemMapper dictItemMapper;

	/**
	 * 群组基本信息：用于访问页面前校验 groupNo 是否合法并展示。 不存在或未启用均返回错误，前端据此判断是“未找到群组”还是“群组已停用”。
	 */
	@GetMapping("/groups/{groupNo}")
	public ApiResponse groupInfo(@PathVariable String groupNo) {
		if (groupNo == null || groupNo.trim().isEmpty()) {
			return ApiResponse.error(400, "群组编号不能为空");
		}
		ClanGroup group = clanGroupMapper.selectOne(new QueryWrapper<ClanGroup>().eq("group_no", groupNo.trim()));
		if (group == null) {
			return ApiResponse.error(404, "未找到该群组：" + groupNo);
		}
		Map<String, Object> data = new HashMap<>();
		data.put("groupNo", group.getGroupNo());
		data.put("groupName", group.getGroupName());
		data.put("status", group.getStatus());
		return ApiResponse.ok(data);
	}

	/**
	 * 群组下的所有部落（按编号排序）。用于页面顶部的“部落X” Tab。
	 */
	@GetMapping("/clans")
	public ApiResponse clans(@RequestParam String groupNo) {
		if (groupNo == null || groupNo.trim().isEmpty()) {
			return ApiResponse.error(400, "群组编号不能为空");
		}
		List<Clan> list = clanMapper
			.selectList(new QueryWrapper<Clan>().eq("group_no", groupNo.trim()).orderByAsc("clan_no"));
		return ApiResponse.ok(list);
	}

	/**
	 * 按部落搜索成员（公开接口，用于报名页“游戏名称”自动补全）。 入参：groupNo(必填)、clanNo(必填)、kw(可选模糊关键字)。
	 * 仅返回该部落下“已加入(member_status=1)且未删除”的成员名称与编号，最多 50 条。
	 */
	@GetMapping("/clan-members")
	public ApiResponse clanMembers(@RequestParam String groupNo, @RequestParam String clanNo,
			@RequestParam(required = false) String kw) {
		if (groupNo == null || groupNo.trim().isEmpty()) {
			return ApiResponse.error(400, "群组编号不能为空");
		}
		if (clanNo == null || clanNo.trim().isEmpty()) {
			return ApiResponse.error(400, "部落编号不能为空");
		}
		String g = groupNo.trim();
		QueryWrapper<ClanMember> qw = new QueryWrapper<>();
		qw.eq("group_no", g).eq("clan_no", clanNo.trim());
		qw.eq("member_status", 1).eq("deleted", 0);
		if (kw != null && !kw.trim().isEmpty()) {
			qw.like("member_name", kw.trim());
		}
		qw.orderByAsc("member_name").last("LIMIT 50");
		List<ClanMember> list = clanMemberMapper.selectList(qw);
		List<Map<String, Object>> result = new ArrayList<>();
		for (ClanMember m : list) {
			Map<String, Object> item = new HashMap<>();
			item.put("memberName", m.getMemberName());
			item.put("memberNo", m.getMemberNo());
			result.add(item);
		}
		return ApiResponse.ok(result);
	}

	/**
	 * 群组下的所有联赛（按创建时间倒序）。用于页面上方切换当前联赛。 不带 leagueNo 参数时也允许为空——此时报名接口会自动取最近一个联赛。
	 */
	@GetMapping("/leagues")
	public ApiResponse leagues(@RequestParam String groupNo) {
		if (groupNo == null || groupNo.trim().isEmpty()) {
			return ApiResponse.error(400, "群组编号不能为空");
		}
		List<League> list = leagueMapper
			.selectList(new QueryWrapper<League>().eq("group_no", groupNo.trim()).orderByDesc("id"));
		return ApiResponse.ok(list);
	}

	/**
	 * 报名情况查询：按 群组 + 联赛 + 部落 过滤，仅返回该范围下的报名记录。 不传 leagueNo 时默认取群组最近一个联赛（与 submit 接口保持一致）。
	 */
	@GetMapping("/signups")
	public ApiResponse signups(@RequestParam String groupNo, @RequestParam(required = false) String clanNo,
			@RequestParam(required = false) String leagueNo) {
		if (groupNo == null || groupNo.trim().isEmpty()) {
			return ApiResponse.error(400, "群组编号不能为空");
		}
		String g = groupNo.trim();

		// 未指定 leagueNo 时取群组最近一个联赛
		String lg = (leagueNo == null || leagueNo.trim().isEmpty()) ? resolveLatestLeagueNo(g) : leagueNo.trim();
		if (lg == null) {
			// 群组下尚无联赛，直接返回空列表
			return ApiResponse.ok(new LeagueSignupListPayload());
		}

		QueryWrapper<LeagueSignup> qw = new QueryWrapper<>();
		qw.eq("group_no", g).eq("league_no", lg);
		if (clanNo != null && !clanNo.trim().isEmpty()) {
			qw.eq("clan_no", clanNo.trim());
		}
		qw.orderByAsc("clan_no").orderByAsc("id");
		List<LeagueSignup> records = leagueSignupMapper.selectList(qw);

		// 回填成员的大本等级/匹配值/战斗力（按 groupNo + memberNo 匹配 clan_member）
		if (!records.isEmpty()) {
			Map<String, ClanMember> memberMap = new HashMap<>();
			List<ClanMember> members = clanMemberMapper.selectList(new QueryWrapper<ClanMember>().eq("group_no", g));
			for (ClanMember m : members) {
				if (m.getMemberNo() != null && !m.getMemberNo().trim().isEmpty()) {
					memberMap.put(m.getMemberNo().trim(), m);
				}
			}
			for (LeagueSignup r : records) {
				ClanMember m = r.getMemberNo() != null ? memberMap.get(r.getMemberNo().trim()) : null;
				if (m == null && r.getMemberName() != null) {
					// 回落：按 memberName 匹配（memberNo 缺失时）
					for (ClanMember cm : members) {
						if (r.getMemberName().equals(cm.getMemberName())) {
							m = cm;
							break;
						}
					}
				}
				if (m != null) {
					r.setThLevel(m.getThLevel());
					r.setMatchValue(m.getMatchValue());
					r.setCombatPower(m.getCombatPower());
				}
			}
		}

		LeagueSignupListPayload payload = new LeagueSignupListPayload();
		payload.setLeagueNo(lg);
		payload.setRecords(records);
		return ApiResponse.ok(payload);
	}

	/**
	 * 联赛战绩查询（公开）。 入参：{ groupNo, clanNo?, leagueNo? }。 行为： 1) groupNo 必传且群组必须存在； 2)
	 * leagueNo 缺省时取群组最近一个联赛； 3) 按 (groupNo + leagueNo) 查询战绩，clanNo 可选过滤； 4)
	 * 返回生效联赛编号与战绩列表（按部落、排名排序），每个战绩回填部落名称。
	 */
	@GetMapping("/records")
	public ApiResponse records(@RequestParam String groupNo, @RequestParam(required = false) String clanNo,
			@RequestParam(required = false) String leagueNo) {
		if (groupNo == null || groupNo.trim().isEmpty()) {
			return ApiResponse.error(400, "群组编号不能为空");
		}
		String g = groupNo.trim();
		if (clanGroupMapper.selectCount(new QueryWrapper<ClanGroup>().eq("group_no", g)) == 0) {
			return ApiResponse.error(400, "群组不存在");
		}

		// 未指定 leagueNo 时取群组最近一个联赛
		String lg = (leagueNo == null || leagueNo.trim().isEmpty()) ? resolveLatestLeagueNo(g) : leagueNo.trim();
		if (lg == null) {
			// 群组下尚无联赛，直接返回空列表
			return ApiResponse.ok(new LeagueRecordListPayload());
		}

		QueryWrapper<LeagueRecord> qw = new QueryWrapper<>();
		qw.eq("group_no", g).eq("league_no", lg);
		if (clanNo != null && !clanNo.trim().isEmpty()) {
			qw.eq("clan_no", clanNo.trim());
		}
		qw.orderByAsc("clan_no").orderByAsc("member_rank").orderByDesc("id");
		List<LeagueRecord> records = leagueRecordMapper.selectList(qw);

		// 查询本联赛报名记录，用于回填战绩的报名状态（战绩导入时 signup_status 通常为空）
		QueryWrapper<LeagueSignup> sw = new QueryWrapper<>();
		sw.eq("group_no", g).eq("league_no", lg);
		if (clanNo != null && !clanNo.trim().isEmpty()) {
			sw.eq("clan_no", clanNo.trim());
		}
		List<LeagueSignup> signups = leagueSignupMapper.selectList(sw);
		// key = clanNo + '|' + (n:memberNo 或 m:memberName)
		Map<String, Integer> signupStatusMap = new HashMap<>();
		for (LeagueSignup s : signups) {
			String base = (s.getClanNo() == null ? "" : s.getClanNo()) + "|";
			if (s.getMemberNo() != null && !s.getMemberNo().trim().isEmpty()) {
				signupStatusMap.put(base + "n:" + s.getMemberNo().trim(), s.getSignupStatus());
			}
			if (s.getMemberName() != null && !s.getMemberName().trim().isEmpty()) {
				signupStatusMap.put(base + "m:" + s.getMemberName().trim(), s.getSignupStatus());
			}
		}

		// 回填部落名称
		Map<String, String> clanNameMap = new HashMap<>();
		if (!records.isEmpty()) {
			Set<String> clanNos = new HashSet<>();
			for (LeagueRecord r : records) {
				if (r.getClanNo() != null)
					clanNos.add(r.getClanNo());
			}
			if (!clanNos.isEmpty()) {
				List<Clan> clans = clanMapper.selectList(new QueryWrapper<Clan>().in("clan_no", clanNos));
				for (Clan c : clans)
					clanNameMap.put(c.getClanNo(), c.getClanName());
			}
		}

		// 回填部落名称 + 报名状态（优先按 memberNo 匹配，回落到 memberName）
		for (LeagueRecord r : records) {
			if (r.getClanNo() != null)
				r.setClanName(clanNameMap.get(r.getClanNo()));
			String base = (r.getClanNo() == null ? "" : r.getClanNo()) + "|";
			Integer st = null;
			if (r.getMemberNo() != null && !r.getMemberNo().trim().isEmpty()) {
				st = signupStatusMap.get(base + "n:" + r.getMemberNo().trim());
			}
			if (st == null && r.getMemberName() != null && !r.getMemberName().trim().isEmpty()) {
				st = signupStatusMap.get(base + "m:" + r.getMemberName().trim());
			}
			r.setSignupStatus(st);
		}

		LeagueRecordListPayload payload = new LeagueRecordListPayload();
		payload.setLeagueNo(lg);
		payload.setRecords(records);
		return ApiResponse.ok(payload);
	}

	/**
	 * 联赛部落成绩查询（公开）。 入参：{ groupNo, clanNo, leagueNo? }。 行为： 1) groupNo 必传且群组必须存在； 2)
	 * leagueNo 缺省时取群组最近一个联赛； 3) 按 (groupNo + leagueNo + clanNo) 查询 league_clan_score
	 * 单条记录； 4) 回填部落名称后直接返回该记录（不存在时返回 null）。 用于结果页「部落战绩」模块展示段位、排名、晋级状态、联赛币等真实数据。
	 */
	@GetMapping("/clan-score")
	public ApiResponse clanScore(@RequestParam String groupNo, @RequestParam String clanNo,
			@RequestParam(required = false) String leagueNo) {
		if (groupNo == null || groupNo.trim().isEmpty()) {
			return ApiResponse.error(400, "群组编号不能为空");
		}
		String g = groupNo.trim();
		if (clanGroupMapper.selectCount(new QueryWrapper<ClanGroup>().eq("group_no", g)) == 0) {
			return ApiResponse.error(400, "群组不存在");
		}

		String lg = (leagueNo == null || leagueNo.trim().isEmpty()) ? resolveLatestLeagueNo(g) : leagueNo.trim();
		if (lg == null) {
			// 群组下尚无联赛，返回 null
			return ApiResponse.ok((LeagueClanScore) null);
		}

		// 预加载 league_tier 字典，将段位值翻译为名称
		List<DictItem> tierItems = dictItemMapper
			.selectList(new QueryWrapper<DictItem>().eq("group_code", "league_tier").orderByAsc("sort"));
		Map<String, String> tierMap = new HashMap<>();
		for (DictItem it : tierItems) {
			tierMap.put(it.getItemValue(), it.getItemName());
		}

		LeagueClanScore score = leagueClanScoreMapper.selectOne(
				new QueryWrapper<LeagueClanScore>().eq("group_no", g).eq("league_no", lg).eq("clan_no", clanNo.trim()));

		if (score != null) {
			Clan clan = clanMapper.selectOne(new QueryWrapper<Clan>().eq("clan_no", clanNo.trim()));
			if (clan != null)
				score.setClanName(clan.getClanName());
			if (score.getTier() != null)
				score.setTierName(tierMap.get(score.getTier()));
		}
		return ApiResponse.ok(score);
	}

	/**
	 * 提交报名。 入参：{ groupNo, clanNo, leagueNo?, memberName, memberNo? }。 行为： 1) leagueNo
	 * 缺省时取群组最近一个联赛； 2) 校验群组/部落/联赛三者必须匹配； 3) 按 (leagueNo + memberNo) 幂等：已存在则更新状态与时间，否则插入。
	 * 4) signupStatus 默认 3（主动报名），与 doSignup 行为一致。
	 */
	@PostMapping("/signup")
	public ApiResponse submit(@RequestBody Map<String, Object> body) {
		String groupNo = asString(body.get("groupNo"));
		String clanNo = asString(body.get("clanNo"));
		String leagueNo = asString(body.get("leagueNo"));
		String memberName = asString(body.get("memberName"));
		String memberNo = asString(body.get("memberNo"));
		Integer signupStatus = body.get("signupStatus") == null ? 3 : toInt(body.get("signupStatus"), 3);

		if (groupNo == null || groupNo.isEmpty())
			return ApiResponse.error("群组编号不能为空");
		if (clanNo == null || clanNo.isEmpty())
			return ApiResponse.error("请选择部落");
		if (memberName == null || memberName.isEmpty())
			return ApiResponse.error("请输入成员名称");

		// 校验群组/部落/联赛三者匹配
		ClanGroup group = clanGroupMapper.selectOne(new QueryWrapper<ClanGroup>().eq("group_no", groupNo));
		if (group == null)
			return ApiResponse.error(404, "群组不存在");

		Clan clan = clanMapper.selectOne(new QueryWrapper<Clan>().eq("clan_no", clanNo).eq("group_no", groupNo));
		if (clan == null)
			return ApiResponse.error(404, "该群组下未找到部落：" + clanNo);

		String lg = (leagueNo == null || leagueNo.isEmpty()) ? resolveLatestLeagueNo(groupNo) : leagueNo;
		if (lg == null)
			return ApiResponse.error("该群组下暂无可报名的联赛，请先创建联赛");

		League league = leagueMapper.selectOne(new QueryWrapper<League>().eq("league_no", lg).eq("group_no", groupNo));
		if (league == null)
			return ApiResponse.error(404, "该群组下未找到联赛：" + lg);

		// 报名时间校验：仅允许在 signupStart ~ signupEnd 区间内提交报名
		LocalDateTime now = LocalDateTime.now();
		if (league.getSignupStart() != null && now.isBefore(league.getSignupStart())) {
			return ApiResponse.error("不在报名时间内");
		}
		if (league.getSignupEnd() != null && now.isAfter(league.getSignupEnd())) {
			return ApiResponse.error("不在报名时间内");
		}

		LeagueSignup existing = leagueSignupMapper
			.selectOne(new QueryWrapper<LeagueSignup>().eq("league_no", lg).eq("member_name", memberName));
		if (existing != null) {
			existing.setSignupStatus(signupStatus);
			existing.setSignupTime(LocalDateTime.now());
			// 若带了 memberNo 顺便补上（避免后续战绩关联缺编号）
			if (memberNo != null && !memberNo.isEmpty()) {
				existing.setMemberNo(memberNo);
			}
			leagueSignupMapper.updateById(existing);
			return ApiResponse.ok(existing);
		}

		LeagueSignup signup = new LeagueSignup();
		signup.setGroupNo(groupNo);
		signup.setClanNo(clanNo);
		signup.setLeagueNo(lg);
		signup.setMemberName(memberName);
		signup.setMemberNo(memberNo);
		signup.setSignupStatus(signupStatus);
		signup.setSignupTime(LocalDateTime.now());
		leagueSignupMapper.insert(signup);
		return ApiResponse.ok(signup);
	}

	// ==================== 内部工具 ====================

	/** 取群组最近一个联赛的编号（按 id 倒序），无则返回 null。 */
	private String resolveLatestLeagueNo(String groupNo) {
		League latest = leagueMapper
			.selectOne(new QueryWrapper<League>().eq("group_no", groupNo).orderByDesc("id").last("LIMIT 1"));
		return latest == null ? null : latest.getLeagueNo();
	}

	private static String asString(Object o) {
		if (o == null)
			return null;
		String s = String.valueOf(o).trim();
		return s.isEmpty() ? null : s;
	}

	private static int toInt(Object o, int def) {
		if (o == null)
			return def;
		try {
			if (o instanceof Number)
				return ((Number) o).intValue();
			return Integer.parseInt(String.valueOf(o).trim());
		}
		catch (Exception e) {
			return def;
		}
	}

	/** 报名查询结果包装（含生效联赛编号，便于前端展示当前在看哪个联赛）。 */
	public static class LeagueSignupListPayload {

		private String leagueNo;

		private List<LeagueSignup> records;

		public String getLeagueNo() {
			return leagueNo;
		}

		public void setLeagueNo(String leagueNo) {
			this.leagueNo = leagueNo;
		}

		public List<LeagueSignup> getRecords() {
			return records;
		}

		public void setRecords(List<LeagueSignup> records) {
			this.records = records;
		}

	}

	/** 战绩查询结果包装（含生效联赛编号，便于前端展示当前在看哪个联赛）。 */
	public static class LeagueRecordListPayload {

		private String leagueNo;

		private List<LeagueRecord> records;

		public String getLeagueNo() {
			return leagueNo;
		}

		public void setLeagueNo(String leagueNo) {
			this.leagueNo = leagueNo;
		}

		public List<LeagueRecord> getRecords() {
			return records;
		}

		public void setRecords(List<LeagueRecord> records) {
			this.records = records;
		}

	}

}
