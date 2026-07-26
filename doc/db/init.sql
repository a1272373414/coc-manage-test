-- ============================================================
-- COC 部落冲突数据后台管理系统 —— 建表 SQL（由当前数据库自动生成）
-- 字符集 utf8mb4 / 引擎 InnoDB
-- 约定：
--   1. 主键 id BIGINT AUTO_INCREMENT
--   2. 公共审计字段：created_at / updated_at / created_by / updated_by / deleted
--   3. 业务表含 group_no 数据隔离键
--   4. 表间关联以「编号」作逻辑外键，不建物理外键
-- 说明：本文件由脚本从线上数据库 SHOW CREATE TABLE 导出，AUTO_INCREMENT 已清除。
-- ============================================================

-- -------------------- sys_user --------------------
CREATE TABLE `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(32) NOT NULL COMMENT '登录账号',
  `password` varchar(64) NOT NULL COMMENT '密码（BCrypt 加密）',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(64) DEFAULT NULL COMMENT 'email',
  `group_no` varchar(32) DEFAULT NULL COMMENT '所属群组编号（超级管理员可空，表示跨群组）',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '启用状态：0=禁用 1=启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -------------------- sys_role --------------------
CREATE TABLE `sys_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码：SUPER_ADMIN/GROUP_OWNER/ADMIN/MEMBER',
  `role_name` varchar(64) DEFAULT NULL COMMENT '角色名称：超级管理员/群主/普通管理员/部落成员',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'enable status 0=disabled 1=enabled',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- -------------------- sys_menu --------------------
CREATE TABLE `sys_menu` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父菜单ID，0=顶级',
  `menu_name` varchar(64) DEFAULT NULL COMMENT '菜单名称',
  `menu_type` tinyint(4) DEFAULT NULL COMMENT '类型：0=目录 1=菜单 2=按钮',
  `permission` varchar(64) DEFAULT NULL COMMENT '权限标识（接口鉴权，如 clan:add）',
  `path` varchar(128) DEFAULT NULL COMMENT '路由/接口路径',
  `component` varchar(128) DEFAULT NULL COMMENT '前端组件路径',
  `icon` varchar(64) DEFAULT NULL COMMENT '菜单图标',
  `sort` int(11) DEFAULT NULL COMMENT '排序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- -------------------- sys_role_menu --------------------
CREATE TABLE `sys_role_menu` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `menu_id` bigint(20) NOT NULL COMMENT '菜单/权限ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单表';

-- -------------------- sys_user_role --------------------
CREATE TABLE `sys_user_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色表';

-- -------------------- sys_config --------------------
CREATE TABLE `sys_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `config_name` varchar(100) NOT NULL COMMENT '配置名',
  `config_value` varchar(500) NOT NULL DEFAULT '' COMMENT '配置值',
  `description` varchar(500) DEFAULT '' COMMENT '描述',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `created_by` varchar(50) DEFAULT NULL,
  `updated_by` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_name` (`config_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- -------------------- Counters --------------------
CREATE TABLE `Counters` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `count` int(11) NOT NULL DEFAULT '1',
  `createdAt` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- -------------------- dict_group --------------------
CREATE TABLE `dict_group` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `group_name` varchar(64) DEFAULT NULL COMMENT '字典组名称',
  `group_code` varchar(32) NOT NULL COMMENT '字典组编号',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '启用状态 0=禁用 1=启用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典组表';

-- -------------------- dict_item --------------------
CREATE TABLE `dict_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `item_name` varchar(64) DEFAULT NULL COMMENT '字典项名称',
  `item_value` varchar(32) DEFAULT NULL COMMENT '字典项值',
  `group_code` varchar(32) NOT NULL COMMENT '字典组编号（逻辑外键 → dict_group.group_code）',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '启用状态 0=禁用 1=启用',
  `sort` int(11) DEFAULT NULL COMMENT '排序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_item` (`group_code`,`item_value`),
  KEY `idx_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典项表';

-- -------------------- clan_group --------------------
CREATE TABLE `clan_group` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `group_name` varchar(64) DEFAULT NULL COMMENT '群组名称',
  `group_no` varchar(32) NOT NULL COMMENT '群组编号',
  `owner_id` bigint(20) DEFAULT NULL COMMENT '群主用户ID（逻辑外键 → sys_user.id）',
  `intro` varchar(500) DEFAULT NULL COMMENT '简介',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态 0=停用 1=启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_no` (`group_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部落群组表';

-- -------------------- clan --------------------
CREATE TABLE `clan` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `clan_name` varchar(64) DEFAULT NULL COMMENT '部落名称',
  `clan_no` varchar(32) NOT NULL COMMENT '部落编号',
  `group_no` varchar(32) NOT NULL COMMENT '所属群组编号',
  `intro` varchar(500) DEFAULT NULL COMMENT '简介',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clan_no` (`clan_no`),
  KEY `idx_group_no` (`group_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部落表';

-- -------------------- clan_member --------------------
CREATE TABLE `clan_member` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `member_name` varchar(64) DEFAULT NULL COMMENT '成员名称',
  `member_no` varchar(32) DEFAULT NULL COMMENT '成员编号',
  `clan_no` varchar(32) NOT NULL COMMENT '所属部落编号',
  `group_no` varchar(32) NOT NULL COMMENT '所属群组编号',
  `member_status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '在组状态 0=已退出 1=已加入',
  `war_status` tinyint(4) DEFAULT NULL COMMENT '参战状态 0=不参战 1=参战（字典项）',
  `intro` varchar(500) DEFAULT NULL COMMENT '简介',
  `th_level` int(11) NOT NULL DEFAULT '0' COMMENT '大本等级',
  `match_value` int(11) NOT NULL DEFAULT '0' COMMENT '匹配值',
  `combat_power` int(11) NOT NULL DEFAULT '0' COMMENT '战斗力',
  `user_id` bigint(20) DEFAULT NULL COMMENT '关联系统用户（可空）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_group_no` (`group_no`),
  KEY `idx_clan_no` (`clan_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部落成员表';

-- -------------------- clan_group_apply --------------------
CREATE TABLE `clan_group_apply` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `group_no` varchar(64) NOT NULL COMMENT '申请加入的群组编号',
  `user_id` bigint(20) NOT NULL COMMENT '申请人用户ID',
  `apply_status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '申请状态：1=申请中 2=同意 3=拒绝',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_group_status` (`group_no`,`apply_status`),
  KEY `idx_user_status` (`user_id`,`apply_status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入组申请表';

-- -------------------- clan_war --------------------
CREATE TABLE `clan_war` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `war_no` varchar(32) NOT NULL COMMENT '部落战编号',
  `clan_no` varchar(32) NOT NULL COMMENT '所属部落编号',
  `group_no` varchar(32) NOT NULL COMMENT '所属群组编号',
  `win_status` tinyint(4) DEFAULT NULL COMMENT '胜利状态：1=胜 2=平 3=败（字典项）',
  `start_time` datetime DEFAULT NULL COMMENT '发起时间',
  `intro` varchar(500) DEFAULT NULL COMMENT '简介',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_war_no` (`war_no`),
  KEY `idx_group_no` (`group_no`),
  KEY `idx_clan_no` (`clan_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部落战表';

-- -------------------- clan_war_record --------------------
CREATE TABLE `clan_war_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `member_name` varchar(64) DEFAULT NULL COMMENT '成员名称',
  `member_no` varchar(32) DEFAULT NULL COMMENT '成员编号',
  `war_no` varchar(32) NOT NULL COMMENT '所属部落战编号',
  `clan_no` varchar(32) NOT NULL COMMENT '所属部落编号',
  `group_no` varchar(32) NOT NULL COMMENT '所属群组编号',
  `atk1_stars` int(11) DEFAULT NULL COMMENT '第一次进攻胜利之星',
  `atk1_rate` int(11) DEFAULT NULL COMMENT '第一次进攻摧毁率（整数百分比，0-100）',
  `atk2_stars` int(11) DEFAULT NULL COMMENT '第二次进攻胜利之星',
  `atk2_rate` int(11) DEFAULT NULL COMMENT '第二次进攻摧毁率（整数百分比，0-100）',
  `actual_attacks` int(11) DEFAULT NULL COMMENT '实进攻次数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_group_no` (`group_no`),
  KEY `idx_war_no` (`war_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部落战成员战绩表';

-- -------------------- league --------------------
CREATE TABLE `league` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `league_name` varchar(64) DEFAULT NULL COMMENT '联赛名称',
  `league_no` varchar(32) NOT NULL COMMENT '联赛编号',
  `signup_start` datetime DEFAULT NULL COMMENT '报名开始时间',
  `signup_end` datetime DEFAULT NULL COMMENT '报名截止时间',
  `group_no` varchar(32) NOT NULL COMMENT '所属群组编号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_league_group_deleted` (`league_no`,`group_no`,`deleted`),
  KEY `idx_group_no` (`group_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联赛表';

-- -------------------- league_clan_score --------------------
CREATE TABLE `league_clan_score` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `league_no` varchar(32) NOT NULL COMMENT '所属联赛编号',
  `clan_no` varchar(32) NOT NULL COMMENT '所属部落编号',
  `group_no` varchar(32) NOT NULL COMMENT '所属群组编号',
  `signup_start` datetime DEFAULT NULL COMMENT '报名开始时间',
  `signup_end` datetime DEFAULT NULL COMMENT '报名截止时间',
  `tier` varchar(32) DEFAULT NULL COMMENT '联赛段位（字典项 league_tier）',
  `result_rank` int(11) DEFAULT NULL COMMENT '本段排名',
  `extra_count` int(11) DEFAULT NULL COMMENT '额外人数',
  `league_coin` int(11) DEFAULT NULL COMMENT '联赛币',
  `extra_coin` int(11) DEFAULT NULL COMMENT '额外币',
  `promote_status` tinyint(4) DEFAULT '0' COMMENT '升降级 0=无 1=晋级 2=降级',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_league_clan` (`league_no`,`clan_no`),
  KEY `idx_group_no` (`group_no`),
  KEY `idx_league_no` (`league_no`),
  KEY `idx_clan_no` (`clan_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联赛部落成绩表';

-- -------------------- league_signup --------------------
CREATE TABLE `league_signup` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `member_name` varchar(64) DEFAULT NULL COMMENT '成员名称',
  `member_no` varchar(32) DEFAULT NULL COMMENT '成员编号',
  `league_no` varchar(32) NOT NULL COMMENT '所属联赛编号',
  `clan_no` varchar(32) NOT NULL COMMENT '所属部落编号',
  `group_no` varchar(32) NOT NULL COMMENT '所属群组编号',
  `signup_status` tinyint(4) DEFAULT NULL COMMENT '报名状态：1=未报名 2=备选报名 3=主动报名（字典项）',
  `signup_time` datetime DEFAULT NULL COMMENT '报名时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_group_no` (`group_no`),
  KEY `idx_league_no` (`league_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联赛报名表';

-- -------------------- league_record --------------------
CREATE TABLE `league_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `member_name` varchar(64) DEFAULT NULL COMMENT '成员名称',
  `member_no` varchar(32) DEFAULT NULL COMMENT '成员编号',
  `member_rank` int(11) DEFAULT NULL COMMENT '排名',
  `league_no` varchar(32) NOT NULL COMMENT '所属联赛编号',
  `clan_no` varchar(32) NOT NULL COMMENT '所属部落编号',
  `group_no` varchar(32) NOT NULL COMMENT '所属群组编号',
  `win_stars` int(11) DEFAULT NULL COMMENT '胜利之星',
  `destroy_rate` int(11) DEFAULT NULL COMMENT '摧毁率（整数百分比，0-100）',
  `actual_attacks` int(11) DEFAULT NULL COMMENT '实进攻次数',
  `required_attacks` int(11) DEFAULT NULL COMMENT '应进攻次数',
  `has_extra` tinyint(4) DEFAULT NULL COMMENT '是否有额外：0=否 1=是',
  `signup_status` tinyint(4) DEFAULT NULL COMMENT '报名状态：1=未报名 2=备选报名 3=主动报名（字典项signup_status）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `created_by` varchar(32) DEFAULT NULL COMMENT '创建者',
  `updated_by` varchar(32) DEFAULT NULL COMMENT '修改者',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_group_no` (`group_no`),
  KEY `idx_league_no` (`league_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联赛成员战绩表';

