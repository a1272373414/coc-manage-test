package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  @Resource
  private DashboardService dashboardService;

  @GetMapping("/overview")
  public ApiResponse overview() {
    return ApiResponse.ok(dashboardService.overview());
  }

  @GetMapping("/war-stat")
  public ApiResponse warStat() {
    return ApiResponse.ok(dashboardService.warStat());
  }

  @GetMapping("/league-rank")
  public ApiResponse leagueRank() {
    return ApiResponse.ok(dashboardService.leagueRank());
  }
}
