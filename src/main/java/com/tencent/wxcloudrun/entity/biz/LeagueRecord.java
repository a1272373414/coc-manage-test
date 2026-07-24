package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 联赛成员战绩。
 */
@Getter
@Setter
@TableName("league_record")
public class LeagueRecord extends BaseEntity {

  private String memberName;
  private String memberNo;
  /** 排名 */
  private Integer memberRank;
  private String leagueNo;
  private String clanNo;
  private String groupNo;
  /** 胜利之星 */
  private Integer winStars;
  /** 摧毁率（整数百分比 0-100） */
  private Integer destroyRate;
  /** 实进攻次数 */
  private Integer actualAttacks;
  /** 应进攻次数 */
  private Integer requiredAttacks;
  /** 是否有额外 0=否 1=是 */
  private Integer hasExtra;
  /** 报名状态（冗余字段，字典项 signup_status）：1=未报名 2=备选报名 3=主动报名 */
  private Integer signupStatus;

  /** 联赛名称（非数据库字段） */
  @TableField(exist = false)
  private String leagueName;

  /** 部落名称（非数据库字段） */
  @TableField(exist = false)
  private String clanName;
}
