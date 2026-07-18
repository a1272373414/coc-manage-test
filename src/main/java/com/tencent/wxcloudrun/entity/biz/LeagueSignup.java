package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 联赛报名记录。以 league_no + member_no 唯一定位。
 */
@Getter
@Setter
@TableName("league_signup")
public class LeagueSignup extends BaseEntity {

  private String memberName;
  private String memberNo;
  private String leagueNo;
  private String clanNo;
  private String groupNo;
  /** 报名状态 1=未报名 2=主动报名 3=协助报名（字典项） */
  private Integer signupStatus;
  private LocalDateTime signupTime;
}
