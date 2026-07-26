package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.biz.Clan;
import com.tencent.wxcloudrun.mapper.ClanMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/clan")
public class ClanController extends BaseCrudController<Clan> {

	@Resource
	private ClanMapper clanMapper;

	@Override
	protected BaseMapper<Clan> mapper() {
		return clanMapper;
	}

	@Override
	protected List<String> keywordFields() {
		return Arrays.asList("clan_name", "clan_no");
	}

}
