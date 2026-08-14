package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 卡牌交换成员表
 */
@Getter
@Setter
@TableName("biz_card_exchange_member")
public class CardExchangeMember extends BaseEntity {

	/** 群组编号（取自 URL） */
	private String groupNo;

	/** 成员名称 */
	private String memberName;

	/** 所属部落（仅标识，复用现有部落下拉） */
	private String tribe;

	/** 卡牌明细（非数据库字段，查询时填充） */
	@TableField(exist = false)
	private java.util.List<CardExchangeMemberCard> cards;
}
