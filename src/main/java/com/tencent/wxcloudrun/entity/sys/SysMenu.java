package com.tencent.wxcloudrun.entity.sys;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 菜单 / 权限。permission 字段即接口所需的权限标识。menu_type: 0 目录 / 1 菜单 / 2 按钮。
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

	private Long parentId;

	/** 0 目录 1 菜单 2 按钮 */
	private Integer menuType;

	private String menuName;

	private String path;

	private String component;

	private String icon;

	private String permission;

	private Integer sort;

}
