package com.tencent.wxcloudrun.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;

import java.util.Collections;
import java.util.Random;
import java.util.Set;

/**
 * 成员编号生成器：生成 10 位“数字 + 小写字母”组成的编号，并在指定 group_no + clan_no 范围内保证唯一。
 */
public final class MemberNoGenerator {

	private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";

	private static final int LENGTH = 10;

	private static final int MAX_ATTEMPT = 200;

	private static final Random RANDOM = new Random();

	private MemberNoGenerator() {
	}

	/** 生成唯一编号（不传已用编号集合，仅按数据库校验唯一）。 */
	public static String generateUniqueMemberNo(ClanMemberMapper mapper, String groupNo, String clanNo) {
		return generateUniqueMemberNo(mapper, groupNo, clanNo, null);
	}

	/**
	 * 生成唯一编号；excludeNos 为本次批次内已生成/占用的编号集合（避免重复查询数据库），可传 null。
	 * 唯一性校验范围：group_no（非空时）+ clan_no（非空时）+ member_no。
	 */
	public static String generateUniqueMemberNo(ClanMemberMapper mapper, String groupNo, String clanNo,
			Set<String> excludeNos) {
		Set<String> ex = excludeNos == null ? Collections.<String>emptySet() : excludeNos;
		for (int attempt = 0; attempt < MAX_ATTEMPT; attempt++) {
			String candidate = randomNo();
			if (ex.contains(candidate)) {
				continue;
			}
			QueryWrapper<ClanMember> qw = new QueryWrapper<ClanMember>();
			if (groupNo != null && !groupNo.trim().isEmpty()) {
				qw.eq("group_no", groupNo.trim());
			}
			if (clanNo != null && !clanNo.trim().isEmpty()) {
				qw.eq("clan_no", clanNo.trim());
			}
			qw.eq("member_no", candidate);
			if (mapper.selectCount(qw) == 0) {
				return candidate;
			}
		}
		throw new IllegalStateException("生成唯一成员编号失败，请稍后重试");
	}

	private static String randomNo() {
		StringBuilder sb = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
		}
		return sb.toString();
	}

}
