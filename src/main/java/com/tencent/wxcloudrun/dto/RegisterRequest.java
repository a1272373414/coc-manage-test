package com.tencent.wxcloudrun.dto;

import lombok.Data;

@Data
public class RegisterRequest {

	private String username;

	private String password;

	private String nickname;

	private String phone;

	/** 可选；不填则系统自动生成 */
	private String groupNo;

}
