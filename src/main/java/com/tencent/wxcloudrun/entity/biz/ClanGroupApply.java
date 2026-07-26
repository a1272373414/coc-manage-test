package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 入组申请表。 一个用户在同一时刻只能存在一条申请中（apply_status=1）的记录。
 */
@Getter
@Setter
@TableName("clan_group_apply")
public class ClanGroupApply extends BaseEntity {

	/** 申请加入的群组编号 */
	private String groupNo;

	/** 申请人用户ID */
	private Long userId;

	/** 申请状态：1=申请中 2=同意 3=拒绝 */
	private Integer applyStatus;

	// ============ 非持久化字段（由 controller 回填用于列表展示） ============
	/** 申请人用户名 */
	@TableField(exist = false)
	private String username;

	/** 申请人昵称 */
	@TableField(exist = false)
	private String nickname;

	/** 目标群组名称 */
	@TableField(exist = false)
	private String groupName;

}
