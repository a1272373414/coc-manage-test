package com.tencent.wxcloudrun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tencent.wxcloudrun.entity.biz.CardExchangeMember;

import java.util.List;
import java.util.Map;

public interface CardExchangeService extends IService<CardExchangeMember> {

	/** 群组是否存在 */
	boolean groupExists(String groupNo);

	/** 查询群组下成员列表（含卡牌明细），按 id 倒序 */
	List<CardExchangeMember> listByGroup(String groupNo);

	/** 新增 / 编辑成员（含卡牌明细）。同一群组(groupNo)+成员名称唯一，忽略已删除数据 */
	CardExchangeMember saveMember(CardExchangeMember member, String groupNo, String operator);

	/** 软删除成员（级联删除卡牌明细，逻辑删除） */
	void removeMember(Long memberId, String groupNo, Long operatorId);

	/**
	 * 查找交换对象
	 * @param currentMemberId 当前选择成员 id
	 * @param matchType       mutual=互利互惠, oneWay=单方受益
	 * @return 按"对方名称+卡牌分类"聚合后的匹配结果
	 */
	List<Map<String, Object>> findMatch(Long currentMemberId, String matchType);
}
