package com.tencent.wxcloudrun.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.biz.CardExchangeMemberCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CardExchangeMemberCardMapper extends BaseMapper<CardExchangeMemberCard> {

	/**
	 * 物理删除卡牌交换成员卡牌明细，绕过 @TableLogic 逻辑删除。
	 */
	int physicalDelete(@Param("ew") Wrapper<CardExchangeMemberCard> wrapper);

}
