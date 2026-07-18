-- 变更说明：为 sys_menu 表补充前端菜单所需的 component、icon 字段
-- 适用版本：在已执行 init.sql 建表的基础上追加，不修改原建表语句
-- 执行方式：mysql -u <用户名> -p <数据库名> < doc/db/alter_sys_menu_add_component_icon.sql

-- 仅在字段不存在时添加，避免重复执行报错
ALTER TABLE sys_menu
  ADD COLUMN component VARCHAR(128) DEFAULT NULL COMMENT '前端组件路径' AFTER path,
  ADD COLUMN icon     VARCHAR(64)  DEFAULT NULL COMMENT '菜单图标' AFTER component;

