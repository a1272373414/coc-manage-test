package com.tencent.wxcloudrun.entity.biz;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 部落群组。group_no 为租户标识，由注册或管理员分配。
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

  /** 群主用户名（非数据库字段，由 Controller 在分页/详情时关联 sys_user 填充） */
  @TableField(exist = false)
  private String ownerName;
}
