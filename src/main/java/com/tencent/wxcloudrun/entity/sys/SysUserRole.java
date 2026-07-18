package com.tencent.wxcloudrun.entity.sys;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户-角色关联。关系表，无审计字段。
 */
@Getter
@Setter
@TableName("sys_user_role")
public class SysUserRole {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private Long roleId;
}
