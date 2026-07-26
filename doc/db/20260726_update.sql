-- 部落成员表：新增大本等级、匹配值、战斗力（均为 int，默认 0）
ALTER TABLE clan_member ADD COLUMN th_level INT NOT NULL DEFAULT 0 COMMENT '大本等级' AFTER intro;
ALTER TABLE clan_member ADD COLUMN match_value INT NOT NULL DEFAULT 0 COMMENT '匹配值' AFTER th_level;
ALTER TABLE clan_member ADD COLUMN combat_power INT NOT NULL DEFAULT 0 COMMENT '战斗力' AFTER match_value;

-- ============ 系统配置表 ============
CREATE TABLE IF NOT EXISTS sys_config (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  config_name  VARCHAR(100) NOT NULL COMMENT '配置名',
  config_value VARCHAR(500) NOT NULL DEFAULT '' COMMENT '配置值',
  description   VARCHAR(500) DEFAULT '' COMMENT '描述',
  created_at    DATETIME     DEFAULT NULL,
  updated_at    DATETIME     DEFAULT NULL,
  created_by    VARCHAR(50)  DEFAULT NULL,
  updated_by    VARCHAR(50)  DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_name (config_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 初始配置数据（数值为占位默认值，可在后台调整；四项得分之和建议为 10000）
INSERT IGNORE INTO sys_config (config_name, config_value, description) VALUES
('attack_score',      '2500', '进攻概率得分'),
('participate_score', '2500', '参赛概率得分'),
('three_star_score',  '2500', '三星概率得分'),
('defense_score',     '2500', '防御概率得分'),
('max_th_level',      '17', '最高大本等级'),
('max_match_value',   '0',  '最高匹配值');

-- 菜单：系统配置（仅超级管理员可见）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, permission, sort, icon) VALUES
(21, '系统配置', 1, 5, '/sys/config', 'sys:config:list', 8, NULL);
-- 角色-菜单关联：仅绑定超级管理员（role_id=1）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 21);

