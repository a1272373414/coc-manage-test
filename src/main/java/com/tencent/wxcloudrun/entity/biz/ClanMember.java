package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 部落成员。member_no 为逻辑主键，关联部落通过 clan_no。
 */
@Getter
@Setter
@TableName("clan_member")
public class ClanMember extends BaseEntity {

	private String memberName;

	private String memberNo;

	private String clanNo;

	private String groupNo;

	/** 在组状态 0=已退出 1=已加入 */
	private Integer memberStatus;

	/** 参战状态 0=不参战 1=参战（字典项） */
	private Integer warStatus;

	private String intro;

	private Long userId;

	/** 大本等级 */
	private Integer thLevel;

	/** 匹配值 */
	private Integer matchValue;

	/** 战斗力 */
	private Integer combatPower;

	/** 备用名称1~5（成员别名，用于导入时按别名匹配同一成员） */
	private String backupName1;

	private String backupName2;

	private String backupName3;

	private String backupName4;

	private String backupName5;

}
