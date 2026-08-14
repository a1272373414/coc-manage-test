package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.IgnoreLogin;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.CardExchangeMember;
import com.tencent.wxcloudrun.service.CardExchangeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

	/** 群组存在性校验（公开） */
	@IgnoreLogin
	@GetMapping("/group/check")
	public ApiResponse checkGroup(@RequestParam String groupNo) {
		Map<String, Object> data = new java.util.HashMap<>(2);
		data.put("exists", cardExchangeService.groupExists(groupNo));
		return ApiResponse.ok(data);
	}

	/** 成员列表（公开，按 URL 群组编号隔离） */
	@IgnoreLogin
	@GetMapping("/members")
	public ApiResponse listMembers(@RequestParam String groupNo) {
		return ApiResponse.ok(cardExchangeService.listByGroup(groupNo));
	}

	/** 新增 / 编辑成员（公开；同群组+成员名称唯一，忽略已删除） */
	@IgnoreLogin
	@PostMapping("/member/save")
	public ApiResponse saveMember(@RequestParam String groupNo, @RequestBody CardExchangeMember member) {
		String operator = currentOperator();
		CardExchangeMember saved = cardExchangeService.saveMember(member, groupNo, operator);
		return ApiResponse.ok(saved);
	}

	/** 删除成员（需群主或群组管理员登录） */
	@DeleteMapping("/member/delete")
	public ApiResponse deleteMember(@RequestParam Long memberId, @RequestParam String groupNo) {
		ApiResponse denied = assertGroupAdmin(groupNo);
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

	/** 校验当前登录用户为群主或群组管理员，且属于该群组 */
	private ApiResponse assertGroupAdmin(String groupNo) {
		if (UserContext.get() == null) {
			return ApiResponse.error(401, "请先登录");
		}
		String ctxGroup = UserContext.getGroupNo();
		if (ctxGroup == null || !ctxGroup.equals(groupNo)) {
			return ApiResponse.error(403, "无权操作该群组");
		}
		List<String> roles = UserContext.get().getRoleCodes();
		boolean admin = roles != null && roles.contains("GROUP_ADMIN");
		if (!admin) {
			return ApiResponse.error(403, "仅群主或群组管理员可删除");
		}
		return null;
	}
}
