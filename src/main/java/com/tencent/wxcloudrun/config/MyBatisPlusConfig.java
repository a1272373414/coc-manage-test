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
   * - 业务表（clan_league_clan_war）按当前登录用户的 group_no 过滤；
   * - 超级管理员（group_no 为空）放行全部数据；
   * - 字典表（dict_*）为全局共享配置，不隔离；
   * - sys_user 同样按 group_no 隔离（仅超级管理员放行），其余 sys_* 表为全局配置不隔离；
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
      // 其余 sys_* 表（角色/菜单/关系表）为全局配置，不隔离
      if (tableName.startsWith("sys_")) {
        // sys_user 需要按 group_no 隔离，仅超级管理员放行（上面已 return），其余角色按组过滤
        return !"sys_user".equals(tableName);
      }
      // 业务表按 group_no 隔离
      return false;
    }
  }
}
