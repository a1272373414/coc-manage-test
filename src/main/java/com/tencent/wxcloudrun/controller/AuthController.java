package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.AuthUser;
import com.tencent.wxcloudrun.config.JwtUtil;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.dto.LoginRequest;
import com.tencent.wxcloudrun.dto.RegisterRequest;
import com.tencent.wxcloudrun.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  @Resource
  private AuthService authService;
  @Resource
  private JwtUtil jwtUtil;

  @PostMapping("/login")
  public ApiResponse login(@RequestBody LoginRequest req) {
    if (req.getUsername() == null || req.getPassword() == null) {
      return ApiResponse.error("用户名和密码不能为空");
    }
    AuthUser user = authService.login(req.getUsername(), req.getPassword());
    String token = jwtUtil.generateToken(user);
    Map<String, Object> data = new HashMap<>();
    data.put("token", token);
    data.put("user", user);
    return ApiResponse.ok(data);
  }

  @PostMapping("/register")
  public ApiResponse register(@RequestBody RegisterRequest req) {
    authService.register(req);
    return ApiResponse.ok();
  }

  @GetMapping("/info")
  public ApiResponse info() {
    AuthUser current = UserContext.get();
    if (current == null) {
      return ApiResponse.error(401, "未登录");
    }
    return ApiResponse.ok(authService.info(current));
  }
}
