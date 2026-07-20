/**
 * 前端环境配置 - 生产环境
 * 用于线上部署，API指向生产后端地址
 * 
 * 部署时需要将此文件重命名为 config.prod.js 并确保正确配置 apiUrl
 */
window.APP_CONFIG = {
  // 环境标识
  env: 'production',
  
  // 后端API地址（部署到云托管后自动替换为实际域名）
  // 方式1: 直接填写完整URL（推荐用于独立域名）
  // apiUrl: 'https://your-cloudrun-service.tencentcloudbase.com',
  
  // 方式2: 使用相对路径（推荐用于同域部署）
  // 当前后端和前端在同一域名下时使用空字符串，会自动使用当前域名
  apiUrl: '',
  
  // 应用标题
  appTitle: '部落冲突部族联赛管理系统',
  
  // 是否开启调试模式（生产环境必须关闭！）
  debug: false,
  
  // 是否显示性能监控面板
  showPerformanceMonitor: false,
  
  // 接口超时时间（毫秒）
  requestTimeout: 30000,
  
  // 是否启用请求日志（生产环境必须关闭！）
  enableRequestLog: false,
  
  // 错误上报地址（可选）
  errorReportUrl: ''
};
