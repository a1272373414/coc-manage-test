package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.entity.sys.SysRole;
import com.tencent.wxcloudrun.entity.sys.SysRoleMenu;
import com.tencent.wxcloudrun.mapper.SysRoleMapper;
import com.tencent.wxcloudrun.mapper.SysRoleMenuMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.tencent.wxcloudrun.util.StreamUtils;

@RestController
@RequestMapping("/api/sys/role")
@SuppressWarnings("all")
public class SysRoleController extends BaseCrudController<SysRole> {

  @Resource
  private SysRoleMapper sysRoleMapper;
  @Resource
  private SysRoleMenuMapper roleMenuMapper;

  @Override
  protected BaseMapper<SysRole> mapper() {
    return sysRoleMapper;
  }

  @Override
  protected List<String> keywordFields() {
    return Arrays.asList("role_code", "role_name");
  }

  /** 查询某个角色已分配的菜单 id 列表（用于分配菜单弹窗回显） */
  @GetMapping("/{id}/menus")
  public ApiResponse getRoleMenus(@PathVariable Long id) {
    List<SysRoleMenu> list = roleMenuMapper.selectList(
        new QueryWrapper<SysRoleMenu>().eq("role_id", id));
    List<Long> menuIds = StreamUtils.mapNonNull(list, SysRoleMenu::getMenuId);
    return ApiResponse.ok(menuIds);
  }

  /**
   * 设置角色的菜单集合：先物理删除再插入，全量替换。
   * menuIds 为 null 或空数组表示清空角色全部菜单。
   * 注意：必须使用 physicalDeleteByRoleId 绕过 MyBatis-Plus 逻辑删除拦截，
   * 否则旧记录未被真正删除会导致 uk_role_menu 唯一索引冲突。
   */
  @PostMapping("/{id}/menus")
  @Transactional
  public ApiResponse assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
    if (sysRoleMapper.selectById(id) == null) {
      return ApiResponse.error("角色不存在");
    }
    roleMenuMapper.physicalDeleteByRoleId(id);
    int inserted = 0;
    if (menuIds != null) {
      // 用 LinkedHashSet 去重，保留顺序，避免前端传重复 id 触发 uk_role_menu 唯一索引冲突
      Set<Long> uniqueMenuIds = new LinkedHashSet<>();
      for (Long menuId : menuIds) {
        if (menuId != null) uniqueMenuIds.add(menuId);
      }
      // 注意：目录节点（menu_type=0）也可能携带权限标识（如 system:manage），
      // 必须保存到 sys_role_menu，否则 toAuthUser() 无法收集到该权限。
      // 回显时由 getRoleMenus 过滤掉目录节点，避免 el-tree 级联全选。
      for (Long menuId : uniqueMenuIds) {
        SysRoleMenu rm = new SysRoleMenu();
        rm.setRoleId(id);
        rm.setMenuId(menuId);
        roleMenuMapper.insert(rm);
        inserted++;
      }
    }
    return ApiResponse.ok(Collections.singletonMap("count", inserted));
  }
}
