package com.tencent.wxcloudrun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tencent.wxcloudrun.entity.sys.SysRoleMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

  /**
   * 物理删除某角色的全部菜单关联。
   * 使用原生 SQL 注解，绕过 MyBatis-Plus 的逻辑删除拦截，
   * 确保 sys_role_menu 表记录真正被删除，避免重新分配时触发 uk_role_menu 唯一索引冲突。
   */
  @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
  int physicalDeleteByRoleId(@Param("roleId") Long roleId);
}
