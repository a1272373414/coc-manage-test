package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 部落战成员战绩。
 */
@Getter
@Setter
@TableName("clan_war_record")
public class ClanWarRecord extends BaseEntity {

  private String memberName;
  private String memberNo;
  private String warNo;
  private String clanNo;
  private String groupNo;
  private Integer atk1Stars;
  private Integer atk1Rate;
  private Integer atk2Stars;
  private Integer atk2Rate;
  private Integer actualAttacks;
}
