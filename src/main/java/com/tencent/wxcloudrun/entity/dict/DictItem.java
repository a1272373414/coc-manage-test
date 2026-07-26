package com.tencent.wxcloudrun.entity.dict;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tencent.wxcloudrun.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典项。全局共享配置。
 */
@Getter
@Setter
@TableName("dict_item")
public class DictItem extends BaseEntity {

	private String groupCode;

	private String itemName;

	private String itemValue;

	private Integer sort;

	private Integer status;

}
