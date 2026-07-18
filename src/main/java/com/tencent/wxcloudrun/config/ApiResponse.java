package com.tencent.wxcloudrun.config;

import lombok.Data;

import java.util.HashMap;

/**
 * 统一接口返回结构。
 * code: 0 表示成功，非 0 表示业务/鉴权错误。
 */
@Data
public final class ApiResponse {

  private Integer code;
  private String errorMsg;
  private Object data;

  private ApiResponse(int code, String errorMsg, Object data) {
    this.code = code;
    this.errorMsg = errorMsg;
    this.data = data;
  }

  public static ApiResponse ok() {
    return new ApiResponse(0, "", new HashMap<>());
  }

  public static ApiResponse ok(Object data) {
    return new ApiResponse(0, "", data);
  }

  public static ApiResponse error(String errorMsg) {
    return new ApiResponse(400, errorMsg, new HashMap<>());
  }

  public static ApiResponse error(int code, String errorMsg) {
    return new ApiResponse(code, errorMsg, new HashMap<>());
  }
}
