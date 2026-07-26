package com.tencent.wxcloudrun.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.entity.biz.ClanWar;
import com.tencent.wxcloudrun.entity.biz.LeagueRecord;
import com.tencent.wxcloudrun.mapper.ClanGroupMapper;
import com.tencent.wxcloudrun.mapper.ClanMapper;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import com.tencent.wxcloudrun.mapper.ClanWarMapper;
import com.tencent.wxcloudrun.mapper.LeagueMapper;
import com.tencent.wxcloudrun.mapper.LeagueRecordMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据看板：概览统计、部落战战绩统计、联赛排行榜。所有统计均受 group_no 租户隔离约束。
 */
@Service
public class DashboardService {

	@Resource
	private ClanGroupMapper clanGroupMapper;

	@Resource
	private ClanMapper clanMapper;

	@Resource
	private ClanMemberMapper memberMapper;

	@Resource
	private LeagueMapper leagueMapper;

	@Resource
	private ClanWarMapper warMapper;

	@Resource
	private LeagueRecordMapper leagueRecordMapper;

	public Map<String, Object> overview() {
		Map<String, Object> data = new HashMap<>();
		data.put("clanGroupCount", clanGroupMapper.selectCount(new QueryWrapper<>()));
		data.put("clanCount", clanMapper.selectCount(new QueryWrapper<>()));
		data.put("memberCount", memberMapper.selectCount(new QueryWrapper<>()));
		data.put("leagueCount", leagueMapper.selectCount(new QueryWrapper<>()));
		data.put("warCount", warMapper.selectCount(new QueryWrapper<>()));
		return data;
	}

	public List<Map<String, Object>> warStat() {
		List<ClanWar> wars = warMapper.selectList(new QueryWrapper<>());
		Map<Integer, Long> counts = wars.stream()
			.collect(Collectors.groupingBy(w -> w.getWinStatus() == null ? -1 : w.getWinStatus(),
					Collectors.counting()));
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map.Entry<Integer, Long> entry : counts.entrySet()) {
			Map<String, Object> item = new HashMap<>();
			item.put("winStatus", entry.getKey());
			item.put("count", entry.getValue());
			result.add(item);
		}
		return result;
	}

	public List<Map<String, Object>> leagueRank() {
		List<LeagueRecord> records = leagueRecordMapper.selectList(new QueryWrapper<>());
		Map<String, RankItem> agg = new LinkedHashMap<>();
		for (LeagueRecord r : records) {
			String key = r.getMemberNo();
			if (key == null) {
				continue;
			}
			RankItem item = agg.computeIfAbsent(key, k -> {
				RankItem i = new RankItem();
				i.setMemberNo(k);
				i.setMemberName(r.getMemberName());
				return i;
			});
			item.setWinStars(item.getWinStars() + (r.getWinStars() == null ? 0 : r.getWinStars()));
			item.setActualAttacks(item.getActualAttacks() + (r.getActualAttacks() == null ? 0 : r.getActualAttacks()));
			item.setDestroyRate(item.getDestroyRate() + (r.getDestroyRate() == null ? 0 : r.getDestroyRate()));
			item.setScore(item.getWinStars() * 3 + item.getActualAttacks());
		}
		return agg.values().stream().sorted((a, b) -> Integer.compare(b.getScore(), a.getScore())).limit(10).map(i -> {
			Map<String, Object> map = new HashMap<>();
			map.put("memberNo", i.getMemberNo() == null ? "" : i.getMemberNo());
			map.put("memberName", i.getMemberName() == null ? "" : i.getMemberName());
			map.put("winStars", i.getWinStars());
			map.put("actualAttacks", i.getActualAttacks());
			map.put("destroyRate", i.getDestroyRate());
			map.put("score", i.getScore());
			return map;
		}).collect(Collectors.toList());
	}

	private static class RankItem {

		private String memberNo;

		private String memberName;

		private int winStars;

		private int actualAttacks;

		private int destroyRate;

		private int score;

		public String getMemberNo() {
			return memberNo;
		}

		public void setMemberNo(String memberNo) {
			this.memberNo = memberNo;
		}

		public String getMemberName() {
			return memberName;
		}

		public void setMemberName(String memberName) {
			this.memberName = memberName;
		}

		public int getWinStars() {
			return winStars;
		}

		public void setWinStars(int winStars) {
			this.winStars = winStars;
		}

		public int getActualAttacks() {
			return actualAttacks;
		}

		public void setActualAttacks(int actualAttacks) {
			this.actualAttacks = actualAttacks;
		}

		public int getDestroyRate() {
			return destroyRate;
		}

		public void setDestroyRate(int destroyRate) {
			this.destroyRate = destroyRate;
		}

		public int getScore() {
			return score;
		}

		public void setScore(int score) {
			this.score = score;
		}

	}

}
