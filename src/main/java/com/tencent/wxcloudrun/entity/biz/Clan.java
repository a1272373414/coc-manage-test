package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 部落。clan_no 为逻辑主键（编号），与成员/联赛/部落战通过编号关联。
 */
@Getter
@Setter
@TableName("clan")
public class Clan extends BaseEntity {

  private String clanName;
  private String clanNo;
  private String groupNo;
  private String intro;
}
