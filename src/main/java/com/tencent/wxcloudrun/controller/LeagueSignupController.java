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

	/**
	 * 分页查询报名列表，支持按联赛编号、部落编号、成员名称、成员编号筛选。 同时回填 leagueName/clanName 便于前端展示"名称 + 编号"。
	 */
	@GetMapping("/page")
	public ApiResponse page(@RequestParam(required = false) String leagueNo,
			@RequestParam(required = false) String clanNo, @RequestParam(required = false) String memberName,
			@RequestParam(required = false) String memberNo, @RequestParam(required = false) String signupStatus,
			@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size) {
		Page<LeagueSignup> page = PageResult.page(current, size);
		QueryWrapper<LeagueSignup> qw = new QueryWrapper<>();
		if (leagueNo != null && !leagueNo.trim().isEmpty())
			qw.eq("league_no", leagueNo.trim());
		if (clanNo != null && !clanNo.trim().isEmpty())
			qw.eq("clan_no", clanNo.trim());
		if (memberName != null && !memberName.trim().isEmpty())
			qw.like("member_name", "%" + memberName.trim() + "%");
		if (memberNo != null && !memberNo.trim().isEmpty())
			qw.like("member_no", "%" + memberNo.trim() + "%");
		if (signupStatus != null && !signupStatus.trim().isEmpty())
			qw.eq("signup_status", signupStatus.trim());
		qw.orderByDesc("id");
		signupMapper.selectPage(page, qw);
		fillNames(page.getRecords());
		return ApiResponse.ok(PageResult.of(page));
	}

	/**
	 * 批量回填 leagueName 和 clanName： 1) 收集当前页所有非空的 leagueNo / clanNo； 2) 一次 SQL 批量查 league
	 * 表和 clan 表（避免 N+1）； 3) 给每条记录回填 leagueName/clanName 非持久化字段（用于列表展示）。
	 */
	@SuppressWarnings("null")
	private void fillNames(List<LeagueSignup> records) {
		if (records == null || records.isEmpty())
			return;
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
			List<League> leagues = leagueMapper.selectList(new QueryWrapper<League>().in("league_no", leagueNos));
			for (League l : leagues) {
				if (l.getLeagueNo() != null)
					leagueNameMap.put(l.getLeagueNo(), l.getLeagueName());
			}
		}
		Map<String, String> clanNameMap = new HashMap<>();
		if (!clanNos.isEmpty()) {
			List<Clan> clans = clanMapper.selectList(new QueryWrapper<Clan>().in("clan_no", clanNos));
			for (Clan c : clans) {
				if (c.getClanNo() != null)
					clanNameMap.put(c.getClanNo(), c.getClanName());
			}
		}
		for (LeagueSignup r : records) {
			if (r.getLeagueNo() != null)
				r.setLeagueName(leagueNameMap.get(r.getLeagueNo()));
			if (r.getClanNo() != null)
				r.setClanName(clanNameMap.get(r.getClanNo()));
		}
	}

	/** 某联赛的报名名单（受 group_no 隔离约束） */
	@GetMapping("/list")
	public ApiResponse list(@RequestParam String leagueNo) {
		List<LeagueSignup> list = signupMapper
			.selectList(new QueryWrapper<LeagueSignup>().eq("league_no", leagueNo).orderByDesc("id"));
		fillNames(list);
		return ApiResponse.ok(list);
	}

	/**
	 * 报名 / 退赛（按 league_no + member_name 幂等更新，member_no 非必填，填写时用于区分同名成员）。 signupTime 由后端自动写入当前时间，前端无需/不许传。
	 */
	@PostMapping
	public ApiResponse signup(@RequestBody LeagueSignup body) {
		return doSignup(body);
	}

	/**
	 * 编辑（前端 createCrud 调用 PUT）：按主键 id 保存，不调用报名接口的“按名称幂等 upsert”，避免编辑名称时新增重复记录。
	 */
	@PutMapping
	public ApiResponse signupUpdate(@RequestBody LeagueSignup body) {
		if (body.getId() == null) {
			return ApiResponse.error("报名记录 id 不能为空");
		}
		LeagueSignup existing = signupMapper.selectById(body.getId());
		if (existing == null) {
			return ApiResponse.error(404, "未找到报名记录");
		}
		if (body.getLeagueNo() != null && !body.getLeagueNo().trim().isEmpty()) {
			String leagueNo = body.getLeagueNo().trim();
			existing.setLeagueNo(leagueNo);
			// 联赛变更时同步更新 group_no
			League league = leagueMapper.selectOne(new QueryWrapper<League>().eq("league_no", leagueNo));
			if (league != null) {
				existing.setGroupNo(league.getGroupNo());
			}
		}
		if (body.getClanNo() != null) {
			existing.setClanNo(body.getClanNo());
		}
		if (body.getMemberName() != null && !body.getMemberName().trim().isEmpty()) {
			existing.setMemberName(body.getMemberName().trim());
		}
		if (body.getMemberNo() != null) {
			String memberNo = body.getMemberNo().trim();
			existing.setMemberNo(memberNo.isEmpty() ? null : memberNo);
		}
		if (body.getSignupStatus() != null) {
			existing.setSignupStatus(body.getSignupStatus());
		}
		signupMapper.updateById(existing);
		return ApiResponse.ok(existing);
	}

	/** 删除报名记录（逻辑删除） */
	@DeleteMapping("/{id}")
	public ApiResponse delete(@PathVariable Long id) {
		signupMapper.deleteById(id);
		return ApiResponse.ok();
	}

	/** 群主修改某条报名状态：按主键 id 更新，避免 memberNo 缺省导致无法定位记录。 */
	@PutMapping("/status")
	public ApiResponse updateStatus(@RequestBody Map<String, Object> body) {
		Object idObj = body.get("id");
		Object statusObj = body.get("signupStatus");
		if (idObj == null)
			return ApiResponse.error("报名记录 id 不能为空");
		if (statusObj == null)
			return ApiResponse.error("报名状态不能为空");
		Long id;
		try {
			id = Long.valueOf(String.valueOf(idObj));
		}
		catch (Exception e) {
			return ApiResponse.error("报名记录 id 不合法");
		}
		int signupStatus;
		try {
			signupStatus = Integer.parseInt(String.valueOf(statusObj));
		}
		catch (Exception e) {
			return ApiResponse.error("报名状态不合法");
		}
		if (signupStatus < 1 || signupStatus > 3)
			return ApiResponse.error("报名状态不合法");
		LeagueSignup existing = signupMapper.selectById(id);
		if (existing == null)
			return ApiResponse.error(404, "未找到报名记录");
		existing.setSignupStatus(signupStatus);
		signupMapper.updateById(existing);
		return ApiResponse.ok();
	}

	/**
	 * 换部落：将某条报名记录转移到目标部落。 权限由前端 isOwner（群主/部落管理员）控制，此处仅要求已登录且非超级管理员（JwtInterceptor 拦截）。
	 * 逻辑： 1) 校验 id、目标 clanNo； 2) 部落未变更时直接返回；
	 * 3) 目标部落 + 同一联赛已存在该成员报名 → 先逻辑删除该重复记录，避免换部落后出现重复报名； 4) 更新当前记录的 clan_no。
	 */
	@PostMapping("/changeClan")
	public ApiResponse changeClan(@RequestBody Map<String, Object> body) {
		Object idObj = body.get("id");
		Object clanObj = body.get("clanNo");
		if (idObj == null) {
			return ApiResponse.error("报名记录 id 不能为空");
		}
		if (clanObj == null || String.valueOf(clanObj).trim().isEmpty()) {
			return ApiResponse.error("目标部落不能为空");
		}
		Long id;
		try {
			id = Long.valueOf(String.valueOf(idObj));
		} catch (Exception e) {
			return ApiResponse.error("报名记录 id 不合法");
		}
		final String targetClanNo = String.valueOf(clanObj).trim();

		LeagueSignup current = signupMapper.selectById(id);
		if (current == null) {
			return ApiResponse.error(404, "未找到报名记录");
		}

		// 部落未变更：直接返回（前端也会预校验）
		if (targetClanNo.equals(current.getClanNo())) {
			return ApiResponse.ok("部落未变更");
		}

		// 校验目标部落存在且属于同一群组（多租户隔离已限定当前 group_no，跨群部落查不到）
		Clan targetClan = clanMapper.selectOne(new QueryWrapper<Clan>().eq("clan_no", targetClanNo));
		if (targetClan == null) {
			return ApiResponse.error("目标部落不存在: " + targetClanNo);
		}

		// 目标部落已存在同一成员的报名数据 → 先逻辑删除，再转移，避免重复报名
		QueryWrapper<LeagueSignup> dupQw = new QueryWrapper<LeagueSignup>()
			.eq("league_no", current.getLeagueNo())
			.eq("clan_no", targetClanNo);
		if (current.getMemberNo() != null && !current.getMemberNo().trim().isEmpty()) {
			dupQw.eq("member_no", current.getMemberNo().trim());
		} else {
			dupQw.eq("member_name", current.getMemberName());
		}
		LeagueSignup duplicate = signupMapper.selectOne(dupQw);
		if (duplicate != null) {
			signupMapper.deleteById(duplicate.getId());
		}

		// 更新当前报名记录的部落编号
		current.setClanNo(targetClanNo);
		signupMapper.updateById(current);
		return ApiResponse.ok(current);
	}

	private ApiResponse doSignup(LeagueSignup body) {
		if (body.getLeagueNo() == null || body.getLeagueNo().trim().isEmpty()) {
			return ApiResponse.error("leagueNo 不能为空");
		}
		if (body.getMemberName() == null || body.getMemberName().trim().isEmpty()) {
			return ApiResponse.error("memberName 不能为空");
		}
		String leagueNo = body.getLeagueNo().trim();
		String memberName = body.getMemberName().trim();
		// 成员编号非必填：为空时按 联赛编号 + 成员名称 定位；填写时进一步区分同名成员
		String memberNo = body.getMemberNo() == null ? null : body.getMemberNo().trim();
		if (memberNo != null && memberNo.isEmpty()) {
			memberNo = null;
		}
		body.setSignupTime(LocalDateTime.now());
		QueryWrapper<LeagueSignup> qw = new QueryWrapper<LeagueSignup>().eq("league_no", leagueNo)
			.eq("member_name", memberName);
		if (memberNo != null) {
			qw.eq("member_no", memberNo);
		}
		LeagueSignup existing = signupMapper.selectOne(qw);
		if (existing != null) {
			if (body.getSignupStatus() != null) {
				existing.setSignupStatus(body.getSignupStatus());
			}
			if (memberNo != null) {
				existing.setMemberNo(memberNo);
			}
			existing.setSignupTime(body.getSignupTime());
			signupMapper.updateById(existing);
			return ApiResponse.ok(existing);
		}
		else {
			body.setId(null);
			body.setLeagueNo(leagueNo);
			body.setMemberName(memberName);
			body.setMemberNo(memberNo);
			signupMapper.insert(body);
			return ApiResponse.ok(body);
		}
	}

	/**
	 * 一键初始化报名数据： 1) 查询指定部落在组状态为"已加入"(member_status=1)的成员 2) 成员默认参战状态 warStatus=1 →
	 * signupStatus=2(备选报名)，warStatus=0 → signupStatus=1(未报名) 3) 唯一性判断：(member_name,
	 * member_no, league_no, clan_no, group_no) - 已存在且 signupStatus=3(主动报名) → 跳过 - 已存在且
	 * signupStatus=1/2 → 先删再插 - 不存在 → 直接插入
	 */
	@PostMapping("/init")
	public ApiResponse initSignup(@RequestParam String leagueNo, @RequestParam String clanNo) {
		// 查联赛获取 group_no
		League league = leagueMapper.selectOne(new QueryWrapper<League>().eq("league_no", leagueNo));
		if (league == null)
			return ApiResponse.error("联赛不存在: " + leagueNo);
		String groupNo = league.getGroupNo();

		// 查询该部落在组状态为已加入的成员
		List<ClanMember> members = clanMemberMapper
			.selectList(new QueryWrapper<ClanMember>().eq("clan_no", clanNo).eq("member_status", 1));

		int inserted = 0, skipped = 0, replaced = 0;
		for (ClanMember m : members) {
			// 映射报名状态
			int signupStatus = (m.getWarStatus() != null && m.getWarStatus() == 1) ? 2 : 1;

			// 查询是否已存在（按 member_name + member_no + league_no + clan_no + group_no 唯一）
			LeagueSignup existing = signupMapper.selectOne(new QueryWrapper<LeagueSignup>().eq("league_no", leagueNo)
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
