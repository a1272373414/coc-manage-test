package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.IgnoreLogin;
import com.tencent.wxcloudrun.config.RoleConstants;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.CardExchangeMember;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.entity.dict.DictItem;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import com.tencent.wxcloudrun.mapper.DictItemMapper;
import com.tencent.wxcloudrun.service.CardExchangeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cardExchange")
public class CardExchangeController {

	@Resource
	private CardExchangeService cardExchangeService;

	@Resource
	private DictItemMapper dictItemMapper;

	@Resource
	private ClanMemberMapper clanMemberMapper;

	/** 公开：查询指定群组下已加入的部落成员名称（供卡牌交换成员名称下拉） */
	@IgnoreLogin
	@GetMapping("/clanMembers")
	public ApiResponse clanMembers(@RequestParam String groupNo) {
		if (groupNo == null || groupNo.trim().isEmpty()) {
			return ApiResponse.error(400, "群组编号不能为空");
		}
		if (!cardExchangeService.groupExists(groupNo)) {
			return ApiResponse.error(404, "未找到该群组");
		}
		QueryWrapper<ClanMember> qw = new QueryWrapper<>();
		qw.eq("group_no", groupNo.trim())
		  .eq("member_status", 1)
		  .orderByAsc("member_name");
		List<ClanMember> list = clanMemberMapper.selectList(qw);
		return ApiResponse.ok(list);
	}

	/** 公开字典项查询：供卡牌交换等公开页面使用，仅返回启用状态的字典项 */
	@IgnoreLogin
	@GetMapping("/dict/{groupCode}")
	public ApiResponse dictItems(@PathVariable String groupCode) {
		if (groupCode == null || groupCode.trim().isEmpty()) {
			return ApiResponse.error(400, "字典分组编码不能为空");
		}
		QueryWrapper<DictItem> qw = new QueryWrapper<>();
		qw.eq("group_code", groupCode.trim()).eq("status", 1).orderByAsc("sort");
		return ApiResponse.ok(dictItemMapper.selectList(qw));
	}

	/** 群组存在性校验（公开） */
	@IgnoreLogin
	@GetMapping("/group/check")
	public ApiResponse checkGroup(@RequestParam String groupNo) {
		Map<String, Object> data = new java.util.HashMap<>(2);
		data.put("exists", cardExchangeService.groupExists(groupNo));
		return ApiResponse.ok(data);
	}

	/** 成员列表（公开，按 URL 群组编号隔离；可选按 tribe 部落过滤） */
	@IgnoreLogin
	@GetMapping("/members")
	public ApiResponse listMembers(@RequestParam String groupNo, @RequestParam(required = false) String tribe) {
		return ApiResponse.ok(cardExchangeService.listByGroup(groupNo, tribe));
	}

	/** 新增 / 编辑成员（公开；同群组+成员名称唯一，忽略已删除） */
	@IgnoreLogin
	@PostMapping("/member/save")
	public ApiResponse saveMember(@RequestParam String groupNo, @RequestBody CardExchangeMember member) {
		String operator = currentOperator();
		CardExchangeMember saved = cardExchangeService.saveMember(member, groupNo, operator);
		return ApiResponse.ok(saved);
	}

	/** 删除成员（需登录，且仅限本群群的群主或部落管理员操作） */
	@DeleteMapping("/member/delete")
	public ApiResponse deleteMember(@RequestParam Long memberId, @RequestParam String groupNo) {
		ApiResponse denied = assertGroupOrLeagueAdmin(groupNo);
		if (denied != null) {
			return denied;
		}
		Long operatorId = UserContext.getUserId();
		cardExchangeService.removeMember(memberId, groupNo, operatorId);
		return ApiResponse.ok();
	}

	/** 查找交换对象（公开） */
	@IgnoreLogin
	@PostMapping("/findMatch")
	public ApiResponse findMatch(@RequestParam Long memberId,
			@RequestParam(defaultValue = "mutual") String matchType) {
		List<Map<String, Object>> result = cardExchangeService.findMatch(memberId, matchType);
		return ApiResponse.ok(result);
	}

	/** 当前操作者标识：登录取用户名，未登录置空 */
	private String currentOperator() {
		if (UserContext.get() != null && UserContext.get().getUsername() != null) {
			return UserContext.get().getUsername();
		}
		return "";
	}

	/** 校验当前登录用户属于该群组，且为群主（GROUP_ADMIN）或部落管理员（LEAGUE_ADMIN）；否则返回错误响应 */
	private ApiResponse assertGroupOrLeagueAdmin(String groupNo) {
		if (UserContext.get() == null) {
			return ApiResponse.error(401, "请先登录");
		}
		String ctxGroup = UserContext.getGroupNo();
		if (ctxGroup == null || !ctxGroup.equals(groupNo)) {
			return ApiResponse.error(403, "无权操作该群组");
		}
		List<String> roles = UserContext.get().getRoleCodes();
		boolean admin = roles != null
				&& (roles.contains(RoleConstants.GROUP_ADMIN) || roles.contains(RoleConstants.LEAGUE_ADMIN));
		if (!admin) {
			return ApiResponse.error(403, "仅群主或部落管理员可删除");
		}
		return null;
	}
}
