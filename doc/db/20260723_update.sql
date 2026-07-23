-- ============================================================
-- 20260723 数据库变更脚本
-- 说明：本文件汇总 2026-07-23 当天所有表结构变动
-- 执行前请备份相关表数据
-- ============================================================

-- 1. clan_member 表：新增"在组状态"列（默认已加入），成员编号改为可空（配合前端非必填）
ALTER TABLE clan_member ADD COLUMN member_status TINYINT NOT NULL DEFAULT 1 COMMENT '在组状态 0=已退出 1=已加入' AFTER group_no;
ALTER TABLE clan_member MODIFY COLUMN member_no VARCHAR(32) DEFAULT NULL COMMENT '成员编号';

-- 2. 删除 Counters 表（示例代码，已弃用）
DROP TABLE IF EXISTS Counters;

-- 4. 新增"联赛段位"字典组及 18 个段位项（部落冲突 CWL 段位：铜→银→金→水晶→大师→冠军，各 III/II/I）
delete from dict_item where group_code = 'league_tier';

INSERT INTO dict_group (group_code, group_name, status, created_at, updated_at)
SELECT 'league_tier', '联赛段位', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM dict_group WHERE group_code = 'league_tier');

INSERT INTO dict_item (group_code, item_value, item_name, sort, status, created_at, updated_at) VALUES
('league_tier', '1',  '铜杯III',    1,  1, NOW(), NOW()),
('league_tier', '2',  '铜杯II',     2,  1, NOW(), NOW()),
('league_tier', '3',  '铜杯I',      3,  1, NOW(), NOW()),
('league_tier', '4',  '银杯III',    4,  1, NOW(), NOW()),
('league_tier', '5',  '银杯II',     5,  1, NOW(), NOW()),
('league_tier', '6',  '银杯I',      6,  1, NOW(), NOW()),
('league_tier', '7',  '金杯III',    7,  1, NOW(), NOW()),
('league_tier', '8',  '金杯II',     8,  1, NOW(), NOW()),
('league_tier', '9',  '金杯I',      9,  1, NOW(), NOW()),
('league_tier', '10', '水晶杯III',  10, 1, NOW(), NOW()),
('league_tier', '11', '水晶杯II',   11, 1, NOW(), NOW()),
('league_tier', '12', '水晶杯I',    12, 1, NOW(), NOW()),
('league_tier', '13', '大师杯III',  13, 1, NOW(), NOW()),
('league_tier', '14', '大师杯II',   14, 1, NOW(), NOW()),
('league_tier', '15', '大师杯I',    15, 1, NOW(), NOW()),
('league_tier', '16', '冠军杯III',  16, 1, NOW(), NOW()),
('league_tier', '17', '冠军杯II',   17, 1, NOW(), NOW()),
('league_tier', '18', '冠军杯I',    18, 1, NOW(), NOW());

-- 4.1 修复已有联赛段位字典项的 sort 值（按段位从低到高 1~18）
UPDATE dict_item SET sort = CAST(item_value AS UNSIGNED) WHERE group_code = 'league_tier';

-- 5. 新增"报名状态"字典组及 3 个状态项
INSERT INTO dict_group (group_code, group_name, status, created_at, updated_at)
SELECT 'signup_status', '报名状态', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM dict_group WHERE group_code = 'signup_status');

INSERT INTO dict_item (group_code, item_value, item_name, sort, status, created_at, updated_at) VALUES
('signup_status', '1', '未报名',   1, 1, NOW(), NOW()),
('signup_status', '2', '备选报名', 2, 1, NOW(), NOW()),
('signup_status', '3', '主动报名', 3, 1, NOW(), NOW());

-- 5.1 更新 league_signup.signup_status 字段注释
ALTER TABLE league_signup MODIFY COLUMN signup_status TINYINT DEFAULT NULL COMMENT '报名状态：1=未报名 2=备选报名 3=主动报名（字典项）';
