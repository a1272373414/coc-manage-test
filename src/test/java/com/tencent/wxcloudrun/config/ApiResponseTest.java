package com.tencent.wxcloudrun.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiResponse 单元测试：验证统一响应封装的工厂方法。
 */
@DisplayName("统一响应封装测试")
class ApiResponseTest {

	@Test
	@DisplayName("ok() - 默认空数据成功响应")
	void ok_noData() {
		ApiResponse resp = ApiResponse.ok();
		assertEquals(0, resp.getCode());
		assertEquals("", resp.getErrorMsg());
		assertNotNull(resp.getData());
	}

	@Test
	@DisplayName("ok(data) - 携带数据的成功响应")
	void ok_withData() {
		ApiResponse resp = ApiResponse.ok("hello");
		assertEquals(0, resp.getCode());
		assertEquals("", resp.getErrorMsg());
		assertEquals("hello", resp.getData());
	}

	@Test
	@DisplayName("error(msg) - 默认错误码 400")
	void error_message() {
		ApiResponse resp = ApiResponse.error("参数错误");
		assertEquals(400, resp.getCode());
		assertEquals("参数错误", resp.getErrorMsg());
	}

	@Test
	@DisplayName("error(code, msg) - 自定义错误码")
	void error_codeAndMessage() {
		ApiResponse resp = ApiResponse.error(403, "无访问权限");
		assertEquals(403, resp.getCode());
		assertEquals("无访问权限", resp.getErrorMsg());
	}

	@Test
	@DisplayName("error(401, ...) - 未登录场景")
	void error_unauthorized() {
		ApiResponse resp = ApiResponse.error(401, "未登录");
		assertEquals(401, resp.getCode());
		assertEquals("未登录", resp.getErrorMsg());
	}

}
