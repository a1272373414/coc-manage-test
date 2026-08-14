package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 卡牌交换成员卡牌表（成员卡牌为一对多）
 */
@Getter
@Setter
@TableName("biz_card_exchange_member_card")
public class CardExchangeMemberCard extends BaseEntity {

	/** 关联成员表 id */
	private Long memberId;

	/** 群组编号（冗余，便于隔离与查询） */
	private String groupNo;

	/** 卡牌名称（精确匹配键） */
	private String cardName;

	/** 卡牌分类（字典 card_category：圣水兵、黑油兵、超级兵、建筑大师基地兵） */
	private String cardCategory;

	/** 卡牌图标 */
	private String cardIcon;

	/** 数量（仅建表使用，业务匹配暂不参与，存在即按多余、不存在即按缺失） */
	private Integer quantity;

	/** 类型：多余 / 缺失（字典 card_type） */
	private String cardType;
}
