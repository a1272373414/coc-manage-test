package com.tencent.wxcloudrun.dto;

import lombok.Data;

import java.util.List;

/**
 * 菜单树节点，用于 /api/auth/info 返回前端构建导航。
 */
@Data
public class MenuNode {
  private Long id;
  private Long parentId;
  /** 0 目录 1 菜单 2 按钮 */
  private Integer menuType;
  private String menuName;
  private String path;
  private String component;
  private String icon;
  private String permission;
  private List<MenuNode> children;
}
