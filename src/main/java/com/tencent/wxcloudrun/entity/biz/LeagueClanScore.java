package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 联赛部落成绩。每条记录代表一个部落在某联赛中的成绩。
 * 通过 league_no 关联 league 表，通过 clan_no 关联 clan 表。
 */
@Getter
@Setter
@TableName("league_clan_score")
public class LeagueClanScore extends BaseEntity {

  private String leagueNo;
  private String clanNo;
  private String groupNo;
  /** 联赛段位（字典项 league_tier） */
  private String tier;
  /** 本段排名 */
  private Integer resultRank;
  /** 额外人数 */
  private Integer extraCount;
  /** 联赛币 */
  private Integer leagueCoin;
  /** 额外币 */
  private Integer extraCoin;
  /** 晋级状态 0=无 1=晋级 2=降级 */
  private Integer promoteStatus;

  /** 联赛名称（非数据库字段） */
  @TableField(exist = false)
  private String leagueName;

  /** 部落名称（非数据库字段） */
  @TableField(exist = false)
  private String clanName;

  /** 联赛段位名称（league_tier 字典翻译，非数据库字段） */
  @TableField(exist = false)
  private String tierName;
}
