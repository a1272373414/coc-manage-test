package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 联赛。league_no 为逻辑主键，关联部落通过 clan_no。
 */
@Getter
@Setter
@TableName("league")
public class League extends BaseEntity {

  private String leagueName;
  private String leagueNo;
  private String clanNo;
  private String groupNo;
  private LocalDateTime signupStart;
  private LocalDateTime signupEnd;
  /** 联赛段位（字典项） */
  private String tier;
  private Integer resultRank;
  private Integer extraCount;
  private Integer leagueCoin;
  private Integer extraCoin;
  /** 晋级状态 1=晋级 2=保级 3=掉级（字典项） */
  private Integer promoteStatus;
  private String intro;
}
