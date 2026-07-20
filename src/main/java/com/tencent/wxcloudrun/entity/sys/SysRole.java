package com.tencent.wxcloudrun.entity.sys;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色。全局配置，不随部落组隔离。
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole extends BaseEntity {

  private String roleCode;
  private String roleName;
  private Integer status;
  private String remark;
}
