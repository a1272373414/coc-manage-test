package com.tencent.wxcloudrun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tencent.wxcloudrun.dto.CompleteExchangeRequest;
import com.tencent.wxcloudrun.entity.biz.CardExchangeMember;
import com.tencent.wxcloudrun.entity.biz.CardExchangeMemberCard;
import com.tencent.wxcloudrun.entity.biz.ClanGroup;
import com.tencent.wxcloudrun.mapper.CardExchangeMemberCardMapper;
import com.tencent.wxcloudrun.mapper.CardExchangeMemberMapper;
import com.tencent.wxcloudrun.mapper.ClanGroupMapper;
import com.tencent.wxcloudrun.service.CardExchangeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CardExchangeServiceImpl extends ServiceImpl<CardExchangeMemberMapper, CardExchangeMember>
		implements CardExchangeService {

	private static final String TYPE_EXTRA = "多余";
	private static final String TYPE_MISSING = "缺失";

	@Resource
	private CardExchangeMemberCardMapper cardMapper;

	@Resource
	private ClanGroupMapper clanGroupMapper;

	@Override
	public boolean groupExists(String groupNo) {
		if (!StringUtils.hasText(groupNo)) {
			return false;
		}
		QueryWrapper<ClanGroup> qw = new QueryWrapper<>();
		qw.eq("group_no", groupNo);
		return clanGroupMapper.selectCount(qw) > 0;
	}

	@Override
	public List<CardExchangeMember> listByGroup(String groupNo, String tribe) {
		QueryWrapper<CardExchangeMember> qw = new QueryWrapper<>();
		qw.eq("group_no", groupNo);
		if (StringUtils.hasText(tribe)) {
			qw.eq("tribe", tribe.trim());
		}
		qw.orderByDesc("id");
		List<CardExchangeMember> members = getBaseMapper().selectList(qw);
		for (CardExchangeMember m : members) {
			m.setCards(loadCards(m.getId(), groupNo));
		}
		return members;
	}

	private List<CardExchangeMemberCard> loadCards(Long memberId, String groupNo) {
		QueryWrapper<CardExchangeMemberCard> qw = new QueryWrapper<>();
		qw.eq("member_id", memberId);
		qw.eq("group_no", groupNo);
		return cardMapper.selectList(qw);
	}

	@Override
	@Transactional
	public CardExchangeMember saveMember(CardExchangeMember member, String groupNo, String operator) {
		if (member == null || !StringUtils.hasText(groupNo)) {
			throw new IllegalArgumentException("群组编号不能为空");
		}
		if (!StringUtils.hasText(member.getMemberName())) {
			throw new IllegalArgumentException("成员名称不能为空");
		}
		String name = member.getMemberName().trim();
		member.setMemberName(name);
		member.setGroupNo(groupNo);

		// 唯一性校验：同一群组 + 成员名称 唯一，忽略已删除数据
		QueryWrapper<CardExchangeMember> dup = new QueryWrapper<>();
		dup.eq("group_no", groupNo);
		dup.eq("member_name", name);
		if (member.getId() != null) {
			dup.ne("id", member.getId());
		}
		if (getBaseMapper().selectCount(dup) > 0) {
			throw new IllegalStateException("同一群组下已存在相同成员名称的成员");
		}

		if (member.getId() == null) {
			if (!StringUtils.hasText(operator)) {
				operator = "";
			}
			member.setCreatedBy(operator);
			member.setUpdatedBy(operator);
			getBaseMapper().insert(member);
		} else {
			CardExchangeMember old = getBaseMapper().selectById(member.getId());
			if (old == null) {
				throw new IllegalArgumentException("未找到该成员");
			}
			old.setMemberName(name);
			old.setTribe(member.getTribe());
			old.setUpdatedAt(LocalDateTime.now());
			old.setUpdatedBy(operator == null ? "" : operator);
			getBaseMapper().updateById(old);
			// 先物理删除旧卡牌明细，再重新插入
			QueryWrapper<CardExchangeMemberCard> del = new QueryWrapper<>();
			del.eq("member_id", member.getId());
			del.eq("group_no", groupNo);
			cardMapper.physicalDelete(del);
			// 注意：保留原始 member 引用，以便后续写入前端传入的 cards
		}

		// 写入卡牌明细
		if (member.getCards() != null) {
			for (CardExchangeMemberCard c : member.getCards()) {
				if (c == null || !StringUtils.hasText(c.getCardName())) {
					continue;
				}
				c.setId(null);
				c.setMemberId(member.getId());
				c.setGroupNo(groupNo);
				if (c.getQuantity() == null) {
					c.setQuantity(0);
				}
				cardMapper.insert(c);
			}
		}
		member.setCards(loadCards(member.getId(), groupNo));
		return member;
	}

	@Override
	@Transactional
	public void removeMember(Long memberId, String groupNo, Long operatorId) {
		if (memberId == null) {
			throw new IllegalArgumentException("成员 id 不能为空");
		}
		CardExchangeMember old = getBaseMapper().selectById(memberId);
		if (old == null) {
			throw new IllegalArgumentException("未找到该成员");
		}
		if (!groupNo.equals(old.getGroupNo())) {
			throw new IllegalArgumentException("成员不属于当前群组");
		}
		// 逻辑删除成员及卡牌明细（@TableLogic 自动置位 deleted）
		QueryWrapper<CardExchangeMemberCard> del = new QueryWrapper<>();
		del.eq("member_id", memberId);
		del.eq("group_no", groupNo);
		cardMapper.delete(del);
		getBaseMapper().deleteById(memberId);
	}

	@Override
	public List<Map<String, Object>> findMatch(Long currentMemberId, String matchType) {
		CardExchangeMember current = getBaseMapper().selectById(currentMemberId);
		if (current == null) {
			throw new IllegalArgumentException("未找到当前成员");
		}
		String groupNo = current.getGroupNo();
		List<CardExchangeMemberCard> myCards = loadCards(current.getId(), groupNo);
		List<CardExchangeMemberCard> myExtra = filterByType(myCards, TYPE_EXTRA);
		List<CardExchangeMemberCard> myMissing = filterByType(myCards, TYPE_MISSING);

		// 同群组其他成员（不含当前）
		QueryWrapper<CardExchangeMember> qw = new QueryWrapper<>();
		qw.eq("group_no", groupNo);
		qw.ne("id", current.getId());
		List<CardExchangeMember> others = getBaseMapper().selectList(qw);

		// 聚合结果：key = 对方成员名称 + "|" + 卡牌分类
		Map<String, Map<String, Object>> groupMap = new LinkedHashMap<>();
		boolean mutual = "mutual".equals(matchType);

		for (CardExchangeMember opp : others) {
			List<CardExchangeMemberCard> oppCards = loadCards(opp.getId(), groupNo);
			List<CardExchangeMemberCard> oppExtra = filterByType(oppCards, TYPE_EXTRA);
			List<CardExchangeMemberCard> oppMissing = filterByType(oppCards, TYPE_MISSING);

			// 我缺且对方多（同分类 + 同名）
			for (CardExchangeMemberCard miss : myMissing) {
				List<CardExchangeMemberCard> oppGives = matchSame(oppExtra, miss);
				if (oppGives.isEmpty()) {
					continue;
				}
				// 我方回赠：必须与当前缺失卡同分类（游戏规则：交换只能在同一分类内进行）
				List<CardExchangeMemberCard> myReturns = mutual
						? pickReturnForMutual(myExtra, oppMissing, miss.getCardCategory())
						: pickReturn(myExtra, miss.getCardCategory());
				if (myReturns.isEmpty()) {
					// 单方受益下若我方无多余卡，则跳过该交换
					continue;
				}
				for (CardExchangeMemberCard oppGive : oppGives) {
					for (CardExchangeMemberCard myReturn : myReturns) {
						addPair(groupMap, opp, miss, oppGive, myReturn);
					}
				}
			}
		}

		return new ArrayList<>(groupMap.values());
	}

	/** 从对方"多余"卡中，找出与我"缺失"卡同分类同名的卡（可能多张） */
	private List<CardExchangeMemberCard> matchSame(List<CardExchangeMemberCard> extras,
			CardExchangeMemberCard target) {
		List<CardExchangeMemberCard> res = new ArrayList<>();
		for (CardExchangeMemberCard c : extras) {
			if (sameCategory(c, target) && sameName(c, target)) {
				res.add(c);
			}
		}
		return res;
	}

	/** 单方受益回赠：仅取同分类的我方多余卡（交换限制在同一分类内） */
	private List<CardExchangeMemberCard> pickReturn(List<CardExchangeMemberCard> myExtra, String category) {
		List<CardExchangeMemberCard> sameCat = new ArrayList<>();
		for (CardExchangeMemberCard c : myExtra) {
			if (category != null && category.equals(c.getCardCategory())) {
				sameCat.add(c);
			}
		}
		if (!sameCat.isEmpty()) {
			List<CardExchangeMemberCard> one = new ArrayList<>();
			one.add(sameCat.get(0));
			return one;
		}
		return new ArrayList<>();
	}

	/** 互利互惠回赠：在我方缺失卡所属分类内，找与对方缺失卡（同分类同名）的一张作为回赠 */
	private List<CardExchangeMemberCard> pickReturnForMutual(List<CardExchangeMemberCard> myExtra,
			List<CardExchangeMemberCard> oppMissing, String category) {
		for (CardExchangeMemberCard miss : oppMissing) {
			if (category != null && !category.equals(miss.getCardCategory())) {
				continue;
			}
			List<CardExchangeMemberCard> hit = matchSame(myExtra, miss);
			if (!hit.isEmpty()) {
				List<CardExchangeMemberCard> one = new ArrayList<>();
				one.add(hit.get(0));
				return one;
			}
		}
		return new ArrayList<>();
	}

	@SuppressWarnings("unchecked")
	private void addPair(Map<String, Map<String, Object>> groupMap, CardExchangeMember opp,
			CardExchangeMemberCard gain, CardExchangeMemberCard oppGive, CardExchangeMemberCard myReturn) {
		// 按对方成员聚合，不再按分类拆分；前端会按分类二次分组展示
		String key = opp.getMemberName();
		Map<String, Object> grp = groupMap.get(key);
		if (grp == null) {
			grp = new LinkedHashMap<>();
			grp.put("memberId", opp.getId());
			grp.put("memberName", opp.getMemberName());
			grp.put("pairs", new ArrayList<Map<String, Object>>());
			groupMap.put(key, grp);
		}
		List<Map<String, Object>> pairs = (List<Map<String, Object>>) grp.get("pairs");
		Map<String, Object> pair = new LinkedHashMap<>();
		pair.put("gainCard", cardView(gain)); // 己方得到的卡（对方多余的、我缺的）
		pair.put("giveCard", cardView(myReturn)); // 己方换出的卡（回赠对方）
		pairs.add(pair);
	}

	private Map<String, Object> cardView(CardExchangeMemberCard c) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("cardName", c.getCardName());
		m.put("cardCategory", c.getCardCategory());
		m.put("cardIcon", c.getCardIcon());
		return m;
	}

	private List<CardExchangeMemberCard> filterByType(List<CardExchangeMemberCard> cards, String type) {
		List<CardExchangeMemberCard> res = new ArrayList<>();
		for (CardExchangeMemberCard c : cards) {
			if (type.equals(c.getCardType())) {
				res.add(c);
			}
		}
		return res;
	}

	private boolean sameCategory(CardExchangeMemberCard a, CardExchangeMemberCard b) {
		String ca = a.getCardCategory() == null ? "" : a.getCardCategory();
		String cb = b.getCardCategory() == null ? "" : b.getCardCategory();
		return ca.equals(cb);
	}

	private boolean sameName(CardExchangeMemberCard a, CardExchangeMemberCard b) {
		String na = a.getCardName() == null ? "" : a.getCardName();
		String nb = b.getCardName() == null ? "" : b.getCardName();
		return na.equals(nb);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void completeExchange(CompleteExchangeRequest request) {
		String groupNo = request.getGroupNo();
		Long selfMemberId = request.getSelfMemberId();
		Long oppMemberId = request.getOppMemberId();
		List<CompleteExchangeRequest.ExchangePair> pairs = request.getPairs();
		if (!StringUtils.hasText(groupNo) || selfMemberId == null || oppMemberId == null || pairs == null
				|| pairs.isEmpty()) {
			throw new IllegalArgumentException("参数不完整");
		}
		for (CompleteExchangeRequest.ExchangePair pair : pairs) {
			// 己方：删除换出的多余卡、删除得到的缺失卡
			deleteCard(groupNo, selfMemberId, pair.getGiveCategory(), pair.getGiveCardName(), TYPE_EXTRA);
			deleteCard(groupNo, selfMemberId, pair.getGainCategory(), pair.getGainCardName(), TYPE_MISSING);
			// 对方：删除缺失的换出卡、删除多余的得到卡
			deleteCard(groupNo, oppMemberId, pair.getGiveCategory(), pair.getGiveCardName(), TYPE_MISSING);
			deleteCard(groupNo, oppMemberId, pair.getGainCategory(), pair.getGainCardName(), TYPE_EXTRA);
		}
	}

	private void deleteCard(String groupNo, Long memberId, String category, String cardName, String cardType) {
		QueryWrapper<CardExchangeMemberCard> qw = new QueryWrapper<>();
		qw.eq("group_no", groupNo);
		qw.eq("member_id", memberId);
		qw.eq("card_category", category);
		qw.eq("card_name", cardName);
		qw.eq("card_type", cardType);
		cardMapper.physicalDelete(qw);
	}
}
