package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.PageResult;
import com.tencent.wxcloudrun.entity.biz.ClanWarRecord;
import com.tencent.wxcloudrun.mapper.ClanWarRecordMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/war/record")
public class ClanWarRecordController extends BaseCrudController<ClanWarRecord> {

	@Resource
	private ClanWarRecordMapper clanWarRecordMapper;

	@Override
	protected BaseMapper<ClanWarRecord> mapper() {
		return clanWarRecordMapper;
	}

	/**
	 * 重写分页：支持按部落战编号、部落编号精确筛选，以及成员名称模糊搜索。
	 */
	@Override
	@GetMapping("/page")
	public ApiResponse page(@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size) {
		Page<ClanWarRecord> page = PageResult.page(current, size);
		QueryWrapper<ClanWarRecord> qw = new QueryWrapper<>();

		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		HttpServletRequest req = attrs != null ? attrs.getRequest() : null;
		String warNo = req != null ? req.getParameter("warNo") : null;
		String clanNo = req != null ? req.getParameter("clanNo") : null;
		String memberName = req != null ? req.getParameter("memberName") : null;

		if (warNo != null && !warNo.trim().isEmpty()) {
			qw.eq("war_no", warNo.trim());
		}
		if (clanNo != null && !clanNo.trim().isEmpty()) {
			qw.eq("clan_no", clanNo.trim());
		}
		if (memberName != null && !memberName.trim().isEmpty()) {
			qw.like("member_name", memberName.trim());
		}
		if (keyword != null && !keyword.trim().isEmpty()) {
			String kw = keyword.trim();
			qw.and(w -> w.like("member_name", kw).or().like("member_no", kw).or().like("war_no", kw));
		}
		qw.orderByDesc("id");
		mapper().selectPage(page, qw);
		return ApiResponse.ok(PageResult.of(page));
	}

}
