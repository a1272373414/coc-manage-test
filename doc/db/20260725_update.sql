-- league_record 表增加排名字段，对应导入数据中的排名
ALTER TABLE league_record ADD COLUMN member_rank INT DEFAULT NULL COMMENT '排名' after member_no;
