-- ============================================================
-- 变更脚本：补齐实体比建表语句多出的列（代码与数据库结构对齐）
-- 适用：在已执行 init.sql 的库（含云数据库）上追加
-- 说明：
--   1. 实体（SysRole.status / SysUser.email / SysMenu.component,icon）
--      比 init.sql 的表多了这些列，导致 MyBatis 查询报 Unknown column。
--   2. 使用 ADD COLUMN IF NOT EXISTS，可重复执行，不会因列已存在而报错。
--   3. 本脚本不改动任何建表语句，符合「表结构变更一律走变更脚本」的约定。
-- 执行方式（任选其一）：
--   - 腾讯云控制台 DMC：粘贴本文件内容执行
--   - 本地有 mysql 客户端：
--       mysql -u <用户> -p springboot_demo < doc/db/alter_sync_entity_columns.sql
-- ============================================================

-- 1) 角色表：补充启用状态列（启动初始化 DataInitializer 会查询该列）
ALTER TABLE sys_role
  ADD COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT 'enable status 0=disabled 1=enabled' AFTER role_name;

-- 2) 用户表：补充邮箱列（登录查询会 SELECT 该列）
ALTER TABLE sys_user
  ADD COLUMN email VARCHAR(64) DEFAULT NULL COMMENT 'email' AFTER phone;

-- 3) 菜单表：补充前端组件路径与图标列
ALTER TABLE sys_menu
  ADD COLUMN component VARCHAR(128) DEFAULT NULL COMMENT '前端组件路径' AFTER path,
  ADD COLUMN icon     VARCHAR(64)  DEFAULT NULL COMMENT '菜单图标' AFTER component;
