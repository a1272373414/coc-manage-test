package com.tencent.wxcloudrun.entity.dict;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典分组。全局共享配置。
 */
@Getter
@Setter
@TableName("dict_group")
public class DictGroup extends BaseEntity {

  private String groupCode;
  private String groupName;
  private String remark;
  private Integer status;
}
