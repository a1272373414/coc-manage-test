package com.tencent.wxcloudrun.config;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.List;

/**
 * 分页结果统一封装。
 */
@Data
public class PageResult<T> {

	private long current;

	private long size;

	private long total;

	private long pages;

	private List<T> records;

	public static <T> PageResult<T> of(IPage<T> page) {
		PageResult<T> result = new PageResult<>();
		result.setCurrent(page.getCurrent());
		result.setSize(page.getSize());
		result.setTotal(page.getTotal());
		result.setPages(page.getPages());
		result.setRecords(page.getRecords());
		return result;
	}

	public static <T> Page<T> page(long current, long size) {
		return new Page<>(current < 1 ? 1 : current, size < 1 ? 10 : size);
	}

}
