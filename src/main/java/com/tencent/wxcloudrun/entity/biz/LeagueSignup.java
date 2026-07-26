package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 联赛报名记录。以 league_no + member_no 唯一定位。
 */
@Getter
@Setter
@TableName("league_signup")
public class LeagueSignup extends BaseEntity {

	private String memberName;

	private String memberNo;

	private String leagueNo;

	private String clanNo;

	private String groupNo;

	/** 报名状态 1=未报名 2=主动报名 3=协助报名（字典项） */
	private Integer signupStatus;

	private LocalDateTime signupTime;

	// ============ 非持久化字段（仅在 controller 回填用于列表展示） ============
	/** 联赛名称（由 LeagueSignupController 在列表查询时回填） */
	@com.baomidou.mybatisplus.annotation.TableField(exist = false)
	private String leagueName;

	/** 部落名称（由 LeagueSignupController 在列表查询时回填） */
	@com.baomidou.mybatisplus.annotation.TableField(exist = false)
	private String clanName;

	/** 大本等级（由 LeagueSignupController 在列表查询时回填，取自 clan_member） */
	@com.baomidou.mybatisplus.annotation.TableField(exist = false)
	private Integer thLevel;

	/** 匹配值（由 LeagueSignupController 在列表查询时回填，取自 clan_member） */
	@com.baomidou.mybatisplus.annotation.TableField(exist = false)
	private Integer matchValue;

	/** 战斗力（由 LeagueSignupController 在列表查询时回填，取自 clan_member） */
	@com.baomidou.mybatisplus.annotation.TableField(exist = false)
	private Integer combatPower;

}
