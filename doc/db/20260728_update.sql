-- 部落成员表新增备用名称字段（成员别名），用于成员导入 / 联赛战绩导入时按别名匹配同一成员
ALTER TABLE clan_member ADD COLUMN backup_name1 VARCHAR(64) DEFAULT NULL COMMENT '备用名称1';
ALTER TABLE clan_member ADD COLUMN backup_name2 VARCHAR(64) DEFAULT NULL COMMENT '备用名称2';
ALTER TABLE clan_member ADD COLUMN backup_name3 VARCHAR(64) DEFAULT NULL COMMENT '备用名称3';
ALTER TABLE clan_member ADD COLUMN backup_name4 VARCHAR(64) DEFAULT NULL COMMENT '备用名称4';
ALTER TABLE clan_member ADD COLUMN backup_name5 VARCHAR(64) DEFAULT NULL COMMENT '备用名称5';
