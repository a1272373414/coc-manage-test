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
  private String errorMsg2;
  private Object data;

  private ApiResponse(int code, String errorMsg, String errorMsg2, Object data) {
    this.code = code;
    this.errorMsg = errorMsg;
    this.errorMsg2 = errorMsg2;
    this.data = data;
  }

  public static ApiResponse ok() {
    return new ApiResponse(0, "", null, new HashMap<>());
  }

  public static ApiResponse ok(Object data) {
    return new ApiResponse(0, "", null, data);
  }

  public static ApiResponse error(String errorMsg) {
    return new ApiResponse(400, errorMsg, null, new HashMap<>());
  }

  public static ApiResponse error(int code, String errorMsg) {
    return new ApiResponse(code, errorMsg, null, new HashMap<>());
  }

  public static ApiResponse error(String errorMsg, String detail) {
    return new ApiResponse(400, errorMsg, detail, new HashMap<>());
  }
}
