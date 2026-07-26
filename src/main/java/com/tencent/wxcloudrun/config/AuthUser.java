package com.tencent.wxcloudrun.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前登录用户上下文信息，由 JWT 解析得到并存入 UserContext。
 */
@Data
public class AuthUser {

	private Long userId;

	private String username;

	private String groupNo;

	private boolean superAdmin;

	private List<String> roleCodes = new ArrayList<>();

	private Set<String> permissions = new HashSet<>();

}
