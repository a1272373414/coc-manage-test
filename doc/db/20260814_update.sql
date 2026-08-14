-- 卡牌交换活动：建表语句
-- 表命名沿用现有 biz_ 前缀；审计字段命名与 BaseEntity 保持一致（created_at/updated_at/created_by/updated_by/deleted）

-- 卡牌交换成员表
CREATE TABLE IF NOT EXISTS `biz_card_exchange_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `group_no` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '群组编号（取自 URL）',
  `member_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '成员名称',
  `tribe` VARCHAR(128) DEFAULT '' COMMENT '所属部落（仅标识，复用现有部落下拉）',
  `created_by` VARCHAR(64) DEFAULT '' COMMENT '创建人',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT '' COMMENT '修改人',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记（0 未删 1 已删）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_member` (`group_no`, `member_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡牌交换成员表';

-- 卡牌交换成员卡牌表
CREATE TABLE IF NOT EXISTS `biz_card_exchange_member_card` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `member_id` BIGINT NOT NULL COMMENT '关联成员表 id',
  `group_no` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '群组编号（冗余，便于隔离与查询）',
  `card_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '卡牌名称（精确匹配键）',
  `card_category` VARCHAR(64) DEFAULT '' COMMENT '卡牌分类（字典 card_category）',
  `card_icon` VARCHAR(512) DEFAULT '' COMMENT '卡牌图标',
  `quantity` INT NOT NULL DEFAULT 0 COMMENT '数量（仅建表使用，业务暂不参与匹配）',
  `card_type` VARCHAR(32) DEFAULT '' COMMENT '类型：多余/缺失（字典 card_type）',
  `created_by` VARCHAR(64) DEFAULT '' COMMENT '创建人',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT '' COMMENT '修改人',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记（0 未删 1 已删）',
  PRIMARY KEY (`id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_group_no` (`group_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡牌交换成员卡牌表';

-- 卡牌交换：字典分组与字典项种子数据
-- 注意：dict_group / dict_item 审计字段（created_at/created_by/updated_at/updated_by/deleted）由表默认值和 MyBatis-Plus 自动填充，此处可省略。
-- 幂等插入：分组用唯一键 group_code 去重；字典项用 (group_code, item_name) 唯一索引去重。

INSERT INTO `dict_group` (`group_code`, `group_name`, `status`)
SELECT 'card_category', '卡牌分类', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `dict_group` WHERE `group_code` = 'card_category');

INSERT INTO `dict_group` (`group_code`, `group_name`, `status`)
SELECT 'card_type', '卡牌类型', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `dict_group` WHERE `group_code` = 'card_type');

INSERT INTO `dict_item` (`group_code`, `item_name`, `item_value`, `sort`, `status`)
SELECT 'card_category', '圣水兵', '圣水兵', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `group_code` = 'card_category' AND `item_name` = '圣水兵');

INSERT INTO `dict_item` (`group_code`, `item_name`, `item_value`, `sort`, `status`)
SELECT 'card_category', '黑油兵', '黑油兵', 2, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `group_code` = 'card_category' AND `item_name` = '黑油兵');

INSERT INTO `dict_item` (`group_code`, `item_name`, `item_value`, `sort`, `status`)
SELECT 'card_category', '超级兵', '超级兵', 3, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `group_code` = 'card_category' AND `item_name` = '超级兵');

INSERT INTO `dict_item` (`group_code`, `item_name`, `item_value`, `sort`, `status`)
SELECT 'card_category', '建筑大师基地兵', '建筑大师基地兵', 4, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `group_code` = 'card_category' AND `item_name` = '建筑大师基地兵');

INSERT INTO `dict_item` (`group_code`, `item_name`, `item_value`, `sort`, `status`)
SELECT 'card_type', '多余', '多余', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `group_code` = 'card_type' AND `item_name` = '多余');

INSERT INTO `dict_item` (`group_code`, `item_name`, `item_value`, `sort`, `status`)
SELECT 'card_type', '缺失', '缺失', 2, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `dict_item` WHERE `group_code` = 'card_type' AND `item_name` = '缺失');
