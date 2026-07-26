/**
 * 前端环境配置 - 开发环境
 * 用于本地开发调试，API指向本地后端
 */
window.APP_CONFIG = {
  // 环境标识
  env: "development",

  // 后端API地址（本地开发）
  apiUrl: "http://localhost:8080",

  // 应用标题（可显示环境标识）
  appTitle: "部落冲突部落联赛管理系统 [DEV]",

  // 是否开启调试模式
  debug: true,

  // 是否显示性能监控面板
  showPerformanceMonitor: false,

  // 接口超时时间（毫秒）
  requestTimeout: 20000,

  // 是否启用请求日志（开发环境建议开启）
  enableRequestLog: true,
};
