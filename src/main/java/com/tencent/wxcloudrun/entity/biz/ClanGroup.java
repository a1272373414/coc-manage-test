package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 部族群组。group_no 为租户标识，由注册或管理员分配。
 */
@Getter
@Setter
@TableName("clan_group")
public class ClanGroup extends BaseEntity {

  private String groupNo;
  private String groupName;
  private Long ownerId;
  private String intro;
  private Integer status;
}
