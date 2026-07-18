package com.tencent.wxcloudrun.entity.sys;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-菜单（权限）关联。关系表，无审计字段。
 */
@Getter
@Setter
@TableName("sys_role_menu")
public class SysRoleMenu {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long roleId;
  private Long menuId;
}
