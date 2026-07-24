-- ============================================================
-- 20260724 数据库变更脚本
-- 说明：拆分 league 表，将部落成绩相关字段迁移到 league_clan_score 表
-- 执行前请备份 league 表数据
-- ============================================================

-- 1. 新建 league_clan_score 表
CREATE TABLE IF NOT EXISTS league_clan_score (
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

-- 2. 将 league 表中已有的部落成绩数据迁移到 league_clan_score
INSERT INTO league_clan_score (league_no, clan_no, group_no, tier, result_rank, extra_count, league_coin, extra_coin, promote_status, created_at, updated_at)
SELECT league_no, clan_no, group_no, tier, result_rank, extra_count, league_coin, extra_coin, promote_status, created_at, updated_at
FROM league
WHERE clan_no IS NOT NULL AND clan_no != '';

-- 3. 从 league 表删除已迁移的列（报名时间和简介保留在 league 表）
ALTER TABLE league DROP INDEX idx_clan_no;
ALTER TABLE league DROP COLUMN clan_no;
ALTER TABLE league DROP COLUMN intro;
ALTER TABLE league DROP COLUMN tier;
ALTER TABLE league DROP COLUMN result_rank;
ALTER TABLE league DROP COLUMN extra_count;
ALTER TABLE league DROP COLUMN league_coin;
ALTER TABLE league DROP COLUMN extra_coin;
ALTER TABLE league DROP COLUMN promote_status;



-- 4. league_clan_score 表增加报名状态冗余字段
ALTER TABLE league_clan_score ADD COLUMN signup_status TINYINT DEFAULT NULL COMMENT '报名状态：1=未报名 2=备选报名 3=主动报名（字典项signup_status）';

-- 5. league_record 表增加报名状态冗余字段
ALTER TABLE league_record ADD COLUMN signup_status TINYINT DEFAULT NULL COMMENT '报名状态：1=未报名 2=备选报名 3=主动报名（字典项signup_status）' after has_extra;

-- 6. league 表去掉 league_no 单一唯一索引，改为 (league_no, group_no, deleted) 联合唯一索引
ALTER TABLE league DROP INDEX uk_league_no;
ALTER TABLE league ADD UNIQUE KEY uk_league_group_deleted (league_no, group_no, deleted);

-- 7. 入组申请表
CREATE TABLE IF NOT EXISTS clan_group_apply (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  group_no VARCHAR(64) NOT NULL COMMENT '申请加入的群组编号',
  user_id BIGINT NOT NULL COMMENT '申请人用户ID',
  apply_status TINYINT NOT NULL DEFAULT 1 COMMENT '申请状态：1=申请中 2=同意 3=拒绝',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  KEY idx_group_status (group_no, apply_status),
  KEY idx_user_status (user_id, apply_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入组申请表';

-- 8. 游客角色（如已存在则忽略）
INSERT IGNORE INTO sys_role (id, role_code, role_name, status) VALUES (5, 'VISITOR', '游客', 1);

-- 9. 入组申请菜单及角色菜单绑定
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, permission, sort, icon) VALUES
(19, '入组申请', 1, 5, '/clan/group/apply', 'group:apply:list', 6, NULL);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 19), (2, 19), (5, 19);

-- 10. 群组成员管理菜单（群主/超管可管理本群组成员：设为部落管理员、踢出）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, permission, sort, icon) VALUES
(20, '群组成员', 1, 5, '/clan/group/user', 'group:user:list', 7, NULL);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 20), (2, 20);
