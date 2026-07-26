-- league_record 表增加排名字段，对应导入数据中的排名
ALTER TABLE league_record ADD COLUMN member_rank INT DEFAULT NULL COMMENT '排名' after member_no;

-- 部落成员表：去掉成员编号唯一索引（原 uk_member_no 为整表唯一，过于严格），
-- 成员唯一性改由应用层按条件校验：填了编号 → 校验编号唯一；没填编号 → 校验名称唯一
-- （均限定同一群组 group_no 内）。
ALTER TABLE clan_member DROP INDEX uk_member_no;
