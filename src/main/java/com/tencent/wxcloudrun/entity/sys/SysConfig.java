package com.tencent.wxcloudrun.entity.sys;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统配置表（全局配置，不随群组隔离，删除为物理删除）。
 * 注意：不继承 BaseEntity，以避免 @TableLogic 逻辑删除。
 */
@Getter
@Setter
@TableName("sys_config")
public class SysConfig {

  @TableId(type = IdType.AUTO)
  private Long id;
  /** 配置名（唯一） */
  private String configName;
  /** 配置值 */
  private String configValue;
  /** 描述 */
  private String description;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;
  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;
  @TableField(fill = FieldFill.INSERT)
  private String createdBy;
  @TableField(fill = FieldFill.INSERT_UPDATE)
  private String updatedBy;
}
