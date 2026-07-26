package com.tencent.wxcloudrun.config;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，将运行时异常转换为统一的 ApiResponse 错误返回。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Value("${debug.errorMsgShow:false}")
	private boolean errorMsgShow;

	@ExceptionHandler(RuntimeException.class)
	public ApiResponse handleRuntimeException(RuntimeException e) {
		log.error("RuntimeException: {}", e.getMessage(), e);
		if (errorMsgShow) {
			return ApiResponse.error(e.getMessage(), buildStackTrace(e));
		}
		return ApiResponse.error(e.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ApiResponse handleException(Exception e) {
		log.error("Exception: {}", e.getMessage(), e);
		if (errorMsgShow) {
			return ApiResponse.error("系统繁忙，请稍后重试", buildStackTrace(e));
		}
		return ApiResponse.error("系统繁忙，请稍后重试");
	}

	private String buildStackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		pw.flush();
		return sw.toString();
	}

}
