-- 部落表添加排序字段
ALTER TABLE clan ADD COLUMN sort BIGINT NOT NULL DEFAULT 0 COMMENT '排序号，列表按此字段正序展示';

-- 历史数据默认按主键顺序填充，保证迁移后顺序不变
UPDATE clan SET sort = id WHERE sort = 0 OR sort IS NULL;
