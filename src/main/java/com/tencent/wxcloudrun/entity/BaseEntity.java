package com.tencent.wxcloudrun.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 基础实体：所有表共有字段（审计字段 + 逻辑删除）。 注意：group_no 不在基类中，仅业务表/部落组/用户表持有，避免无该列的表查询报错。
 */
@Getter
@Setter
public abstract class BaseEntity {

	@TableId(type = IdType.AUTO)
	private Long id;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	@TableField(fill = FieldFill.INSERT)
	private String createdBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updatedBy;

	@TableLogic
	private Integer deleted;

}
