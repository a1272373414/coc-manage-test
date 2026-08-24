package com.tencent.wxcloudrun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.biz.CardExchangeMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CardExchangeMemberMapper extends BaseMapper<CardExchangeMember> {

	/**
	 * 物理删除卡牌交换成员，绕过 @TableLogic 逻辑删除。
	 */
	@Delete("delete from biz_card_exchange_member where id = #{id}")
	int physicalDeleteById(@Param("id") Long id);
}
