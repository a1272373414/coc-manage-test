package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 部落战。war_no 为逻辑主键。
 */
@Getter
@Setter
@TableName("clan_war")
public class ClanWar extends BaseEntity {

  private String warNo;
  private String clanNo;
  private String groupNo;
  /** 胜利状态 1=胜 2=平 3=败（字典项） */
  private Integer winStatus;
  private LocalDateTime startTime;
  private String intro;
}
