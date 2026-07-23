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
