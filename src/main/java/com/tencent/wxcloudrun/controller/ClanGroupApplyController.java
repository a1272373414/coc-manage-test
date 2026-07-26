package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.AuthUser;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.config.RoleConstants;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.ClanGroup;
import com.tencent.wxcloudrun.entity.biz.ClanGroupApply;
import com.tencent.wxcloudrun.entity.sys.SysRole;
import com.tencent.wxcloudrun.entity.sys.SysUser;
import com.tencent.wxcloudrun.entity.sys.SysUserRole;
import com.tencent.wxcloudrun.mapper.ClanGroupApplyMapper;
import com.tencent.wxcloudrun.mapper.ClanGroupMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMapper;
import com.tencent.wxcloudrun.mapper.SysUserMapper;
import com.tencent.wxcloudrun.mapper.SysUserRoleMapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 入组申请管理。 游客可发起申请；群主/部落组管理员可审批本群组申请。
 */
@RestController
@RequestMapping("/api/clan/group/apply")
public class ClanGroupApplyController {

	@Autowired
	private ClanGroupApplyMapper applyMapper;

	@Autowired
	private ClanGroupMapper clanGroupMapper;

	@Autowired
	private SysUserMapper userMapper;

	@Autowired
	private SysUserRoleMapper userRoleMapper;

	@Autowired
	private SysRoleMapper roleMapper;

	/** 提交入组申请（游客/任何登录用户） */
	@PostMapping
	public ApiResponse create(@RequestBody ClanGroupApply body) {
		AuthUser user = UserContext.get();
		if (user == null)
			return ApiResponse.error("请先登录");
		if (body.getGroupNo() == null || body.getGroupNo().isEmpty()) {
			return ApiResponse.error("请选择要申请的群组");
		}
		// 一个用户仅允许存在一条申请中的数据
		QueryWrapper<ClanGroupApply> qw = new QueryWrapper<>();
		qw.eq("user_id", user.getUserId()).eq("apply_status", 1);
		if (applyMapper.selectCount(qw) > 0) {
			return ApiResponse.error(409, "您已有一条申请中的入组申请，请勿重复提交");
		}
		body.setUserId(user.getUserId());
		body.setApplyStatus(1);
		applyMapper.insert(body);
		return ApiResponse.ok(body);
	}

	/**
	 * 分页查询。 - 超级管理员：全部 - 群主/部落组管理员：本群组（含作为 owner 的群组） - 其他（游客/普通成员）：仅自己的申请
	 */
	@GetMapping("/page")
	public ApiResponse page(@RequestParam(defaultValue = "1") int current, @RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String groupNo, @RequestParam(required = false) Integer applyStatus) {
		AuthUser user = UserContext.get();
		if (user == null)
			return ApiResponse.error("请先登录");

		Page<ClanGroupApply> page = new Page<>(current, size);
		QueryWrapper<ClanGroupApply> qw = new QueryWrapper<>();
		if (groupNo != null && !groupNo.isEmpty()) {
			qw.eq("group_no", groupNo);
		}
		if (applyStatus != null) {
			qw.eq("apply_status", applyStatus);
		}

		if (user.isSuperAdmin()) {
			// 超管：无额外过滤
		}
		else if (user.getRoleCodes().contains(RoleConstants.GROUP_ADMIN)) {
			Set<String> groupNos = managedGroupNos(user);
			if (groupNos.isEmpty()) {
				return ApiResponse.ok(PageResult.of(page));
			}
			qw.in("group_no", new ArrayList<>(groupNos));
		}
		else {
			qw.eq("user_id", user.getUserId());
		}

		Page<ClanGroupApply> result = applyMapper.selectPage(page, qw);
		fillExtra(result.getRecords());
		return ApiResponse.ok(PageResult.of(result));
	}

	/** 同意申请 */
	@PutMapping("/{id}/approve")
	public ApiResponse approve(@PathVariable Long id) {
		ClanGroupApply apply = applyMapper.selectById(id);
		if (apply == null)
			return ApiResponse.error("申请记录不存在");
		if (apply.getApplyStatus() != null && apply.getApplyStatus() != 1) {
			return ApiResponse.error("该申请已处理，请勿重复操作");
		}
		if (!canHandle(apply))
			return ApiResponse.error("无权限处理该申请");

		apply.setApplyStatus(2);
		applyMapper.updateById(apply);

		// 审批通过后，将申请人加入该群组（如无群组）并赋予普通成员角色
		SysUser applicant = userMapper.selectById(apply.getUserId());
		if (applicant != null) {
			if (applicant.getGroupNo() == null || applicant.getGroupNo().isEmpty()) {
				applicant.setGroupNo(apply.getGroupNo());
			}
			ensureMemberRole(apply.getUserId());
			removeVisitorRole(apply.getUserId());
			userMapper.updateById(applicant);
		}
		return ApiResponse.ok(apply);
	}

	/** 拒绝申请 */
	@PutMapping("/{id}/reject")
	public ApiResponse reject(@PathVariable Long id) {
		ClanGroupApply apply = applyMapper.selectById(id);
		if (apply == null)
			return ApiResponse.error("申请记录不存在");
		if (!canHandle(apply))
			return ApiResponse.error("无权限处理该申请");
		apply.setApplyStatus(3);
		applyMapper.updateById(apply);
		return ApiResponse.ok(apply);
	}

	/**
	 * 撤销/删除申请。 申请人可撤销自己的“申请中”记录；管理员可删除本组记录。
	 */
	@DeleteMapping("/{id}")
	public ApiResponse delete(@PathVariable Long id) {
		ClanGroupApply apply = applyMapper.selectById(id);
		if (apply == null)
			return ApiResponse.error("申请记录不存在");
		AuthUser user = UserContext.get();
		if (user == null)
			return ApiResponse.error("请先登录");

		boolean isOwner = apply.getUserId() != null && apply.getUserId().equals(user.getUserId());
		if (!isOwner && !canHandle(apply)) {
			return ApiResponse.error("无权限删除该申请");
		}
		if (isOwner && (apply.getApplyStatus() == null || apply.getApplyStatus() != 1)) {
			return ApiResponse.error("只能撤销申请中的记录");
		}
		applyMapper.deleteById(id);
		return ApiResponse.ok();
	}

	// ==================== 私有辅助方法 ====================

	/** 获取当前用户可管理的所有群组编号 */
	private Set<String> managedGroupNos(AuthUser user) {
		Set<String> set = new HashSet<>();
		if (user.getGroupNo() != null && !user.getGroupNo().isEmpty()) {
			set.add(user.getGroupNo());
		}
		List<ClanGroup> owned = clanGroupMapper
			.selectList(new QueryWrapper<ClanGroup>().eq("owner_id", user.getUserId()));
		for (ClanGroup g : owned) {
			if (g.getGroupNo() != null && !g.getGroupNo().isEmpty()) {
				set.add(g.getGroupNo());
			}
		}
		return set;
	}

	/** 判断当前用户是否有权处理指定申请 */
	private boolean canHandle(ClanGroupApply apply) {
		AuthUser user = UserContext.get();
		if (user == null)
			return false;
		if (user.isSuperAdmin())
			return true;
		if (user.getRoleCodes().contains(RoleConstants.GROUP_ADMIN)) {
			return apply.getGroupNo() != null && managedGroupNos(user).contains(apply.getGroupNo());
		}
		return false;
	}

	/** 移除游客角色 */
	private void removeVisitorRole(Long userId) {
		SysRole visitorRole = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", RoleConstants.VISITOR));
		if (visitorRole == null || visitorRole.getId() == null)
			return;
		userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", userId).eq("role_id", visitorRole.getId()));
	}

	/** 确保用户拥有普通成员角色 */
	private void ensureMemberRole(Long userId) {
		SysRole memberRole = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", RoleConstants.MEMBER));
		if (memberRole == null || memberRole.getId() == null)
			return;
		QueryWrapper<SysUserRole> qw = new QueryWrapper<>();
		qw.eq("user_id", userId).eq("role_id", memberRole.getId());
		if (userRoleMapper.selectCount(qw) == 0) {
			SysUserRole ur = new SysUserRole();
			ur.setUserId(userId);
			ur.setRoleId(memberRole.getId());
			userRoleMapper.insert(ur);
		}
	}

	/** 回填用户名、昵称、群组名称 */
	private void fillExtra(List<ClanGroupApply> records) {
		if (records == null || records.isEmpty())
			return;
		Set<Long> userIds = new HashSet<>();
		Set<String> groupNos = new HashSet<>();
		for (ClanGroupApply r : records) {
			if (r.getUserId() != null)
				userIds.add(r.getUserId());
			if (r.getGroupNo() != null && !r.getGroupNo().isEmpty())
				groupNos.add(r.getGroupNo());
		}
		if (!userIds.isEmpty()) {
			List<SysUser> users = userMapper.selectBatchIds(new ArrayList<>(userIds));
			for (ClanGroupApply r : records) {
				for (SysUser u : users) {
					if (u.getId().equals(r.getUserId())) {
						r.setUsername(u.getUsername());
						r.setNickname(u.getNickname());
						break;
					}
				}
			}
		}
		if (!groupNos.isEmpty()) {
			List<ClanGroup> groups = clanGroupMapper
				.selectList(new QueryWrapper<ClanGroup>().in("group_no", new ArrayList<>(groupNos)));
			for (ClanGroupApply r : records) {
				for (ClanGroup g : groups) {
					if (g.getGroupNo() != null && g.getGroupNo().equals(r.getGroupNo())) {
						r.setGroupName(g.getGroupName());
						break;
					}
				}
			}
		}
	}

}
