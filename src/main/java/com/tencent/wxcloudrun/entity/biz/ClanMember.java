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
  /** 参战状态 0=不参战 1=参战（字典项） */
  private Integer warStatus;
  private String intro;
  private Long userId;
}
