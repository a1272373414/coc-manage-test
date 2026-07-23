package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 联赛。league_no 为逻辑主键。
 * 部落成绩（段位/排名/联赛币/升降级等）拆分到 league_clan_score 表，
 * 通过 league_no 关联。
 */
@Getter
@Setter
@TableName("league")
public class League extends BaseEntity {

  private String leagueName;
  private String leagueNo;
  private String groupNo;
  private LocalDateTime signupStart;
  private LocalDateTime signupEnd;
}
