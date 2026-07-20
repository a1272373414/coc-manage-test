package com.tencent.wxcloudrun.config;

/**
 * 角色与权限常量。权限标识与菜单表 sys_menu.permission 一一对应，由拦截器校验。
 */
public final class RoleConstants {

  /** 超级管理员：跨部落组（group_no 为空），可管理平台全部数据 */
  public static final String SUPER_ADMIN = "SUPER_ADMIN";
  /** 部落组管理员：管理本人所属 group_no 下的成员与部落 */
  public static final String GROUP_ADMIN = "GROUP_ADMIN";
  /** 赛事管理员：负责联赛与部落战组织 */
  public static final String LEAGUE_ADMIN = "LEAGUE_ADMIN";
  /** 普通成员：仅查看 */
  public static final String MEMBER = "MEMBER";

  /** 系统管理类权限，仅 SUPER_ADMIN / GROUP_ADMIN 拥有 */
  public static final String PERM_SYSTEM_MANAGE = "system:manage";

  private RoleConstants() {
  }
}
