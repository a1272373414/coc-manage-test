package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.entity.sys.SysMenu;
import com.tencent.wxcloudrun.mapper.SysMenuMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sys/menu")
public class SysMenuController extends BaseCrudController<SysMenu> {

  @Resource
  private SysMenuMapper sysMenuMapper;

  @Override
  protected BaseMapper<SysMenu> mapper() {
    return sysMenuMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("menu_name", "permission");
  }

  /**
   * 返回完整菜单树（按 sort 升序），供角色分配菜单弹窗使用。
   * 数据结构：[{ id, parentId, menuName, menuType, permission, children: [...] }]
   * 健壮性：能处理 parentId 为 null/0/自身/指向不存在节点 等各种边界情况，
   *        这些节点都会被作为 root 显示，不会丢失。
   */
  @GetMapping("/tree")
  public ApiResponse tree() {
    List<SysMenu> all = sysMenuMapper.selectList(new QueryWrapper<SysMenu>().orderByAsc("sort").orderByAsc("id"));
    Map<Long, SysMenuNode> nodeMap = new HashMap<>();
    List<SysMenuNode> roots = new ArrayList<>();
    // 第一次遍历：建立 id → node 映射
    for (SysMenu m : all) {
      SysMenuNode n = new SysMenuNode();
      n.id = m.getId();
      n.parentId = m.getParentId() == null ? 0L : m.getParentId();
      n.menuName = m.getMenuName();
      n.menuType = m.getMenuType();
      n.permission = m.getPermission();
      n.path = m.getPath();
      n.children = new ArrayList<>();
      nodeMap.put(n.id, n);
    }
    // 第二次遍历：根据 parentId 挂载到父节点或作为 root
    for (SysMenuNode n : nodeMap.values()) {
      SysMenuNode parent = null;
      // parentId 为 0/null、等于自身 id、或指向不存在的节点 → 都作为 root
      if (n.parentId != null && n.parentId != 0L && !n.parentId.equals(n.id)) {
        parent = nodeMap.get(n.parentId);
      }
      if (parent != null) {
        parent.children.add(n);
      } else {
        roots.add(n);
      }
    }
    return ApiResponse.ok(roots);
  }

  /** 内部节点 DTO，避免直接暴露 SysMenu 的审计字段 */
  public static class SysMenuNode {
    public Long id;
    public Long parentId;
    public String menuName;
    public Integer menuType;
    public String permission;
    public String path;
    public List<SysMenuNode> children;
  }
}
