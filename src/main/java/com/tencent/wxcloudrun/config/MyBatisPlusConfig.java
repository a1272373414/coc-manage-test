package com.tencent.wxcloudrun.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：
 * 1. 分页插件
 * 2. 部落组（group_no）多租户隔离插件 —— 核心安全机制
 */
@Configuration
public class MyBatisPlusConfig {

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    // 多租户隔离插件（必须位于分页插件之前）
    interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new GroupTenantHandler()));
    // 分页插件
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
    return interceptor;
  }

  /**
   * 部落组隔离处理器：
   * - 业务表（clan_league_clan_war 等）按当前登录用户的 group_no 过滤；
   * - 超级管理员（group_no 为空）放行全部数据；
   * - 字典表（dict_*）为全局共享配置，不隔离；
   * - sys_* 表（含 sys_user）统一不通过插件隔离，sys_user 的 group_no 过滤由代码手动控制；
   * - 无登录上下文（启动初始化、公开接口）时全部放行，由接口层鉴权控制。
   */
  public static class GroupTenantHandler implements TenantLineHandler {

    private static final String TENANT_COLUMN = "group_no";

    @Override
    public Expression getTenantId() {
      String groupNo = UserContext.getGroupNo();
      return new StringValue(groupNo == null ? "" : groupNo);
    }

    @Override
    public String getTenantIdColumn() {
      return TENANT_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
      // 无登录上下文（启动初始化、公开接口）时放行，由接口层鉴权
      if (UserContext.get() == null) {
        return true;
      }
      // 字典表全局共享
      if (tableName.startsWith("dict_")) {
        return true;
      }
      // 超级管理员放行全部业务数据
      if (UserContext.isSuperAdmin()) {
        return true;
      }
      // sys_* 表（角色/菜单/关系表/用户表）为全局配置，不通过多租户插件隔离。
      // sys_user 的 group_no 过滤由各 Controller 在代码中手动控制（如 SysUserController.page）。
      if (tableName.startsWith("sys_")) {
        return true;
      }
      // 业务表按 group_no 隔离
      // clan_group 作为群组元数据，申请入组时需要全局搜索，不做租户隔离
      if ("clan_group".equals(tableName)) {
        return true;
      }
      if ("clan_group_apply".equals(tableName)) {
        return true;
      }
      return false;
    }
  }
}
