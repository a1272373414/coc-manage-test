-- ============================================================
-- COC 部落冲突数据后台管理系统 —— 建表 SQL
-- 字符集 utf8mb4 / 引擎 InnoDB
-- 约定：
--   1. 主键 id BIGINT AUTO_INCREMENT
--   2. 公共审计字段：created_at / updated_at / created_by / updated_by / deleted
--   3. 业务表含 group_no 数据隔离键
--   4. 编号类 VARCHAR(32) 唯一索引；状态/枚举 TINYINT 默认 0
--   5. 表间关联以「编号」作逻辑外键，不建物理外键
--   6. 时间字段默认 CURRENT_TIMESTAMP，updated_at 自动更新
-- ============================================================

-- -------------------- 系统 / 权限表 --------------------

-- 用户表
CREATE TABLE sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username    VARCHAR(32)  NOT NULL COMMENT '登录账号',
  password    VARCHAR(64)  NOT NULL COMMENT '密码（BCrypt 加密）',
  nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
  phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  group_no    VARCHAR(32)  DEFAULT NULL COMMENT '所属群组编号（超级管理员可空，表示跨群组）',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '启用状态：0=禁用 1=启用',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE sys_role (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  role_code   VARCHAR(32)  NOT NULL COMMENT '角色编码：SUPER_ADMIN/GROUP_OWNER/ADMIN/MEMBER',
  role_name   VARCHAR(64)  DEFAULT NULL COMMENT '角色名称：超级管理员/群主/普通管理员/部落成员',
  remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 菜单表
CREATE TABLE sys_menu (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0=顶级',
  menu_name   VARCHAR(64)  DEFAULT NULL COMMENT '菜单名称',
  menu_type   TINYINT      DEFAULT NULL COMMENT '类型：0=目录 1=菜单 2=按钮',
  permission  VARCHAR(64)  DEFAULT NULL COMMENT '权限标识（接口鉴权，如 clan:add）',
  path        VARCHAR(128) DEFAULT NULL COMMENT '路由/接口路径',
  sort        INT          DEFAULT NULL COMMENT '排序',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- 角色菜单表
CREATE TABLE sys_role_menu (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  role_id     BIGINT       NOT NULL COMMENT '角色ID',
  menu_id     BIGINT       NOT NULL COMMENT '菜单/权限ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单表';

-- 用户角色表
CREATE TABLE sys_user_role (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  user_id     BIGINT       NOT NULL COMMENT '用户ID',
  role_id     BIGINT       NOT NULL COMMENT '角色ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色表';

-- -------------------- 数据字典表（全局，无 group_no） --------------------

-- 数据字典组表
CREATE TABLE dict_group (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  group_name  VARCHAR(64)  DEFAULT NULL COMMENT '字典组名称',
  group_code  VARCHAR(32)  NOT NULL COMMENT '字典组编号',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '启用状态 0=禁用 1=启用',
  remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_code (group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典组表';

-- 数据字典项表
CREATE TABLE dict_item (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  item_name   VARCHAR(64)  DEFAULT NULL COMMENT '字典项名称',
  item_value  VARCHAR(32)  DEFAULT NULL COMMENT '字典项值',
  group_code  VARCHAR(32)  NOT NULL COMMENT '字典组编号（逻辑外键 → dict_group.group_code）',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '启用状态 0=禁用 1=启用',
  sort        INT          DEFAULT NULL COMMENT '排序',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_item (group_code, item_value),
  KEY idx_group_code (group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典项表';

-- -------------------- 业务表（均含 group_no 隔离键） --------------------

-- 部落群组表
CREATE TABLE clan_group (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  group_name  VARCHAR(64)  DEFAULT NULL COMMENT '群组名称',
  group_no    VARCHAR(32)  NOT NULL COMMENT '群组编号',
  owner_id    BIGINT       DEFAULT NULL COMMENT '群主用户ID（逻辑外键 → sys_user.id）',
  intro       VARCHAR(500) DEFAULT NULL COMMENT '简介',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0=停用 1=启用',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_no (group_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部落群组表';

-- 部落表
CREATE TABLE clan (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  clan_name   VARCHAR(64)  DEFAULT NULL COMMENT '部落名称',
  clan_no     VARCHAR(32)  NOT NULL COMMENT '部落编号',
  group_no    VARCHAR(32)  NOT NULL COMMENT '所属群组编号',
  intro       VARCHAR(500) DEFAULT NULL COMMENT '简介',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_clan_no (clan_no),
  KEY idx_group_no (group_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部落表';

-- 部落成员表
CREATE TABLE clan_member (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  member_name VARCHAR(64)  DEFAULT NULL COMMENT '成员名称',
  member_no   VARCHAR(32)  DEFAULT NULL COMMENT '成员编号',
  clan_no     VARCHAR(32)  NOT NULL COMMENT '所属部落编号',
  group_no    VARCHAR(32)  NOT NULL COMMENT '所属群组编号',
  member_status TINYINT   NOT NULL DEFAULT 1 COMMENT '在组状态 0=已退出 1=已加入',
  war_status  TINYINT      DEFAULT NULL COMMENT '参战状态 0=不参战 1=参战（字典项）',
  intro       VARCHAR(500) DEFAULT NULL COMMENT '简介',
  user_id     BIGINT       DEFAULT NULL COMMENT '关联系统用户（可空）',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_no (member_no),
  KEY idx_group_no (group_no),
  KEY idx_clan_no (clan_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部落成员表';

-- 联赛表
CREATE TABLE league (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  league_name   VARCHAR(64)  DEFAULT NULL COMMENT '联赛名称',
  league_no     VARCHAR(32)  NOT NULL COMMENT '联赛编号',
  group_no      VARCHAR(32)  NOT NULL COMMENT '所属群组编号',
  signup_start  DATETIME     DEFAULT NULL COMMENT '报名开始时间',
  signup_end    DATETIME     DEFAULT NULL COMMENT '报名截止时间',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by    VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by    VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_league_group_deleted (league_no, group_no, deleted),
  KEY idx_group_no (group_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联赛表';

-- 联赛部落成绩表（从 league 表拆分，每条 = 一个部落在某联赛中的成绩）
CREATE TABLE league_clan_score (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  league_no      VARCHAR(32)  NOT NULL COMMENT '所属联赛编号',
  clan_no        VARCHAR(32)  NOT NULL COMMENT '所属部落编号',
  group_no       VARCHAR(32)  NOT NULL COMMENT '所属群组编号',
  tier           VARCHAR(32)  DEFAULT NULL COMMENT '联赛段位（字典项 league_tier）',
  result_rank    INT          DEFAULT NULL COMMENT '本段排名',
  extra_count    INT          DEFAULT NULL COMMENT '额外人数',
  league_coin    INT          DEFAULT NULL COMMENT '联赛币',
  extra_coin     INT          DEFAULT NULL COMMENT '额外币',
  promote_status TINYINT     DEFAULT 0 COMMENT '升降级 0=无 1=晋级 2=降级',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by     VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by     VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_league_clan (league_no, clan_no),
  KEY idx_group_no (group_no),
  KEY idx_league_no (league_no),
  KEY idx_clan_no (clan_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联赛部落成绩表';

-- 联赛报名表
CREATE TABLE league_signup (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  member_name   VARCHAR(64)  DEFAULT NULL COMMENT '成员名称',
  member_no     VARCHAR(32)  DEFAULT NULL COMMENT '成员编号',
  league_no     VARCHAR(32)  NOT NULL COMMENT '所属联赛编号',
  clan_no       VARCHAR(32)  NOT NULL COMMENT '所属部落编号',
  group_no      VARCHAR(32)  NOT NULL COMMENT '所属群组编号',
  signup_status TINYINT      DEFAULT NULL COMMENT '报名状态：1=未报名 2=备选报名 3=主动报名（字典项）',
  signup_time   DATETIME     DEFAULT NULL COMMENT '报名时间',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by    VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by    VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  KEY idx_group_no (group_no),
  KEY idx_league_no (league_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联赛报名表';

-- 联赛成员战绩表
CREATE TABLE league_record (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  member_name      VARCHAR(64)  DEFAULT NULL COMMENT '成员名称',
  member_no        VARCHAR(32)  DEFAULT NULL COMMENT '成员编号',
  league_no        VARCHAR(32)  NOT NULL COMMENT '所属联赛编号',
  clan_no          VARCHAR(32)  NOT NULL COMMENT '所属部落编号',
  group_no         VARCHAR(32)  NOT NULL COMMENT '所属群组编号',
  win_stars        INT          DEFAULT NULL COMMENT '胜利之星',
  destroy_rate     INT          DEFAULT NULL COMMENT '摧毁率（整数百分比，0-100）',
  actual_attacks   INT          DEFAULT NULL COMMENT '实进攻次数',
  required_attacks INT          DEFAULT NULL COMMENT '应进攻次数',
  has_extra        TINYINT      DEFAULT NULL COMMENT '是否有额外：0=否 1=是',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by       VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by       VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  KEY idx_group_no (group_no),
  KEY idx_league_no (league_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联赛成员战绩表';

-- 部落战表
CREATE TABLE clan_war (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  war_no      VARCHAR(32)  NOT NULL COMMENT '部落战编号',
  clan_no     VARCHAR(32)  NOT NULL COMMENT '所属部落编号',
  group_no    VARCHAR(32)  NOT NULL COMMENT '所属群组编号',
  win_status  TINYINT      DEFAULT NULL COMMENT '胜利状态：1=胜 2=平 3=败（字典项）',
  start_time  DATETIME     DEFAULT NULL COMMENT '发起时间',
  intro       VARCHAR(500) DEFAULT NULL COMMENT '简介',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by  VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_war_no (war_no),
  KEY idx_group_no (group_no),
  KEY idx_clan_no (clan_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部落战表';

-- 部落战成员战绩表
CREATE TABLE clan_war_record (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  member_name     VARCHAR(64)  DEFAULT NULL COMMENT '成员名称',
  member_no       VARCHAR(32)  DEFAULT NULL COMMENT '成员编号',
  war_no          VARCHAR(32)  NOT NULL COMMENT '所属部落战编号',
  clan_no         VARCHAR(32)  NOT NULL COMMENT '所属部落编号',
  group_no        VARCHAR(32)  NOT NULL COMMENT '所属群组编号',
  atk1_stars      INT          DEFAULT NULL COMMENT '第一次进攻胜利之星',
  atk1_rate       INT          DEFAULT NULL COMMENT '第一次进攻摧毁率（整数百分比，0-100）',
  atk2_stars      INT          DEFAULT NULL COMMENT '第二次进攻胜利之星',
  atk2_rate       INT          DEFAULT NULL COMMENT '第二次进攻摧毁率（整数百分比，0-100）',
  actual_attacks  INT          DEFAULT NULL COMMENT '实进攻次数',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  created_by      VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by      VARCHAR(32)  DEFAULT NULL COMMENT '修改者',
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  KEY idx_group_no (group_no),
  KEY idx_war_no (war_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部落战成员战绩表';

-- 入组申请表
CREATE TABLE clan_group_apply (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  group_no      VARCHAR(32)  NOT NULL COMMENT '申请加入的群组编号',
  user_id       BIGINT       NOT NULL COMMENT '申请人用户ID',
  apply_status  TINYINT      NOT NULL DEFAULT 1 COMMENT '申请状态：1=申请中 2=同意 3=拒绝',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by    VARCHAR(32)  DEFAULT NULL COMMENT '创建者',
  updated_by    VARCHAR(32)  DEFAULT NULL COMMENT '更新者',
  deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  KEY idx_group_status (group_no, apply_status),
  KEY idx_user_status (user_id, apply_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入组申请表';
