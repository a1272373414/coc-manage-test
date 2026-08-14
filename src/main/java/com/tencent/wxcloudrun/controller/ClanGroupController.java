package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.IgnoreLogin;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.config.RoleConstants;
import com.tencent.wxcloudrun.entity.biz.Clan;
import com.tencent.wxcloudrun.entity.biz.ClanGroup;
import com.tencent.wxcloudrun.entity.sys.SysRole;
import com.tencent.wxcloudrun.entity.sys.SysUser;
import com.tencent.wxcloudrun.entity.sys.SysUserRole;
import com.tencent.wxcloudrun.mapper.ClanGroupMapper;
import com.tencent.wxcloudrun.mapper.ClanMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMapper;
import com.tencent.wxcloudrun.mapper.SysUserMapper;
import com.tencent.wxcloudrun.mapper.SysUserRoleMapper;
import com.tencent.wxcloudrun.util.StreamUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clan/group")
@SuppressWarnings("all")
public class ClanGroupController extends BaseCrudController<ClanGroup> {

	@Resource
	private ClanGroupMapper clanGroupMapper;

	@Resource
	private ClanMapper clanMapper;

	@Resource
	private SysUserMapper sysUserMapper;

	@Resource
	private SysRoleMapper sysRoleMapper;

	@Resource
	private SysUserRoleMapper sysUserRoleMapper;

	@Override
	protected BaseMapper<ClanGroup> mapper() {
		return clanGroupMapper;
	}

	@Override
	protected List<String> keywordFields() {
		return Arrays.asList("group_no", "group_name");
	}

	/**
	 * 重写新增：插入群组后，若指定了 ownerId，同步绑定群主： 1) 更新 sys_user.group_no 为群组的 group_no 2) 为该用户添加
	 * GROUP_ADMIN 角色绑定（已存在则跳过）
	 */
	@Override
	@PostMapping
	public ApiResponse create(@RequestBody ClanGroup body) {
		body.setId(null);
		clanGroupMapper.insert(body);
		if (body.getOwnerId() != null) {
			bindOwner(body.getOwnerId(), body.getGroupNo());
		}
		return ApiResponse.ok(body);
	}

	/**
	 * 重写更新：若 ownerId 发生变化，同步绑定/解绑群主： 1) 解绑旧群主：清空 sys_user.group_no + 删除 GROUP_ADMIN 角色绑定
	 * 2) 绑定新群主：设置 sys_user.group_no + 添加 GROUP_ADMIN 角色绑定 注意：先用 selectById 取旧记录的
	 * ownerId，再 updateById，避免覆盖丢失。
	 */
	@Override
	@PutMapping
	public ApiResponse update(@RequestBody ClanGroup body) {
		if (body.getId() == null) {
			return ApiResponse.error("id 不能为空");
		}
		ClanGroup old = clanGroupMapper.selectById(body.getId());
		Long oldOwnerId = old != null ? old.getOwnerId() : null;
		Long newOwnerId = body.getOwnerId();

		clanGroupMapper.updateById(body);

		if (!Objects.equals(oldOwnerId, newOwnerId)) {
			if (oldOwnerId != null) {
				unbindOwner(oldOwnerId);
			}
			if (newOwnerId != null) {
				bindOwner(newOwnerId, body.getGroupNo());
			}
		}
		return ApiResponse.ok(body);
	}

	/**
	 * 绑定群主： - 设置 sys_user.group_no 为群组的 group_no - 添加 GROUP_ADMIN 角色绑定（若不存在）
	 */
	private void bindOwner(Long userId, String groupNo) {
		SysUser user = sysUserMapper.selectById(userId);
		if (user != null) {
			user.setGroupNo(groupNo);
			sysUserMapper.updateById(user);
		}
		Long ownerRoleId = getOwnerRoleId();
		if (ownerRoleId != null) {
			SysUserRole existing = sysUserRoleMapper
				.selectOne(new QueryWrapper<SysUserRole>().eq("user_id", userId).eq("role_id", ownerRoleId));
			if (existing == null) {
				SysUserRole ur = new SysUserRole();
				ur.setUserId(userId);
				ur.setRoleId(ownerRoleId);
				sysUserRoleMapper.insert(ur);
			}
		}
	}

	/**
	 * 解绑群主： - 清空 sys_user.group_no - 删除 GROUP_ADMIN 角色绑定
	 */
	private void unbindOwner(Long userId) {
		SysUser user = sysUserMapper.selectById(userId);
		if (user != null) {
			user.setGroupNo(null);
			sysUserMapper.updateById(user);
		}
		Long ownerRoleId = getOwnerRoleId();
		if (ownerRoleId != null) {
			sysUserRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", userId).eq("role_id", ownerRoleId));
		}
	}

	/** 查询 GROUP_ADMIN（部落组管理员/群主）角色 ID，缓存避免重复查询 */
	private Long cachedOwnerRoleId = null;

	private Long getOwnerRoleId() {
		if (cachedOwnerRoleId != null)
			return cachedOwnerRoleId;
		SysRole role = sysRoleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", RoleConstants.GROUP_ADMIN));
		cachedOwnerRoleId = role != null ? role.getId() : null;
		return cachedOwnerRoleId;
	}

	/** 重写分页接口：查询完成后批量关联 sys_user 填充 ownerName，避免 N+1 查询 */
	@Override
	@GetMapping("/page")
	public ApiResponse page(@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size) {
		Page<ClanGroup> page = PageResult.page(current, size);
		QueryWrapper<ClanGroup> qw = new QueryWrapper<>();
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
		qw.orderByDesc("id");
		mapper().selectPage(page, qw);

		List<ClanGroup> records = page.getRecords();
		if (records != null && !records.isEmpty()) {
			// 收集所有非空 ownerId，批量查询用户名
			Set<Long> ownerIds = StreamUtils.mapNonNullToSet(records, ClanGroup::getOwnerId);
			if (!ownerIds.isEmpty()) {
				List<SysUser> users = sysUserMapper.selectBatchIds(ownerIds);
				Map<Long, String> idToName = new HashMap<>();
				for (SysUser u : users) {
					// 优先显示昵称，其次用户名
					idToName.put(u.getId(),
							u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername());
				}
				for (ClanGroup g : records) {
					if (g.getOwnerId() != null) {
						g.setOwnerName(idToName.get(g.getOwnerId()));
					}
				}
			}
		}
		return ApiResponse.ok(PageResult.of(page));
	}

	/** 重写详情接口：填充 ownerName */
	@Override
	@GetMapping("/{id}")
	public ApiResponse getById(@PathVariable Long id) {
		ClanGroup group = clanGroupMapper.selectById(id);
		if (group != null && group.getOwnerId() != null) {
			SysUser user = sysUserMapper.selectById(group.getOwnerId());
			if (user != null) {
				group.setOwnerName(user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname()
						: user.getUsername());
			}
		}
		return ApiResponse.ok(group);
	}

	/**
	 * 群组下的所有部落（按编号排序）。公开接口，供卡牌交换等公开页面选择所属部落。
	 * 公开调用（@IgnoreLogin）时 UserContext 为空，TenantLineInnerInterceptor 会自动忽略 clan 表的多租户注入，
	 * 因此此处显式 eq("group_no", ...) 即可正确按群组隔离。
	 */
	@IgnoreLogin
	@GetMapping("/clans")
	public ApiResponse clans(@RequestParam String groupNo) {
		if (groupNo == null || groupNo.trim().isEmpty()) {
			return ApiResponse.error(400, "群组编号不能为空");
		}
		if (clanGroupMapper.selectOne(new QueryWrapper<ClanGroup>().eq("group_no", groupNo.trim())) == null) {
			return ApiResponse.error(404, "未找到该群组：" + groupNo);
		}
		List<Clan> list = clanMapper
			.selectList(new QueryWrapper<Clan>().eq("group_no", groupNo.trim()).orderByAsc("clan_no"));
		return ApiResponse.ok(list);
	}

}
