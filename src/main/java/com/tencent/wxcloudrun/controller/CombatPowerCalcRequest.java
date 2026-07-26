package com.tencent.wxcloudrun.controller;

/**
 * 一键计算战斗力请求参数。
 */
public class CombatPowerCalcRequest {

  /** 目标部落编号 */
  private String clanNo;
  /** 进攻概率得分（权重，四项权重之和建议等于 10000） */
  private Integer attackScore;
  /** 参赛概率得分 */
  private Integer participateScore;
  /** 三星概率得分 */
  private Integer threeStarScore;
  /** 防御概率得分 */
  private Integer defenseScore;

  public String getClanNo() {
    return clanNo;
  }

  public void setClanNo(String clanNo) {
    this.clanNo = clanNo;
  }

  public Integer getAttackScore() {
    return attackScore;
  }

  public void setAttackScore(Integer attackScore) {
    this.attackScore = attackScore;
  }

  public Integer getParticipateScore() {
    return participateScore;
  }

  public void setParticipateScore(Integer participateScore) {
    this.participateScore = participateScore;
  }

  public Integer getThreeStarScore() {
    return threeStarScore;
  }

  public void setThreeStarScore(Integer threeStarScore) {
    this.threeStarScore = threeStarScore;
  }

  public Integer getDefenseScore() {
    return defenseScore;
  }

  public void setDefenseScore(Integer defenseScore) {
    this.defenseScore = defenseScore;
  }
}
