package com.tencent.wxcloudrun.entity.sys;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 系统用户。group_no 为空表示超级管理员（跨组）。
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {

  private String username;
  @JsonIgnore
  private String password;
  private String nickname;
  private String phone;
  private String email;
  private String groupNo;
  /** 1 启用 0 禁用 */
  private Integer status;

  @TableField(exist = false)
  private List<String> roleCodes;

  @TableField(exist = false)
  private List<String> permissions;

  /** 已分配的角色 id 列表（非数据库字段，由 Controller 在分页/详情时关联 sys_user_role 填充） */
  @TableField(exist = false)
  private List<Long> roleIds;
}
