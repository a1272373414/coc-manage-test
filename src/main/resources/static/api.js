/* 部落冲突部落联赛管理系统 — 前端接口层、鉴权状态、通用 API 封装 */
(function () {
  const { reactive } = Vue;

  /**
   * 获取API基础地址
   * 优先级：URL参数 > APP_CONFIG配置 > 当前域名(生产环境) > localhost:8080(默认)
   */
  function getApiBase() {
    // 1. 支持通过 ?api=http://host:port 覆盖后端地址（临时测试用）
    const params = new URLSearchParams(location.search);
    const urlParam = params.get('api');
    if (urlParam) return urlParam;
    
    // 2. 从环境配置读取
    if (window.APP_CONFIG && window.APP_CONFIG.apiUrl !== undefined && window.APP_CONFIG.apiUrl !== '') {
      return window.APP_CONFIG.apiUrl;
    }
    
    // 3. 生产环境：如果未配置具体URL，则使用当前页面所在域名（适用于同域部署）
    if (window.APP_CONFIG && window.APP_CONFIG.env === 'production') {
      return location.origin;
    }
    
    // 4. 默认值：本地开发环境
    return 'http://localhost:8080';
  }

  const API_BASE = getApiBase();
  const CONFIG = window.APP_CONFIG || { debug: false, enableRequestLog: false, requestTimeout: 20000 };
  
  // 调试输出（仅开发环境且开启了日志）
  function log(...args) {
    if (CONFIG.debug && CONFIG.enableRequestLog) {
      console.log('[COC-API]', ...args);
    }
  }

  log('环境:', CONFIG.env || 'unknown');
  log('API地址:', API_BASE);

  const http = axios.create({ baseURL: API_BASE, timeout: CONFIG.requestTimeout || 20000 });

  http.interceptors.request.use((config) => {
    const t = localStorage.getItem('coc_token');
    if (t) config.headers.Authorization = 'Bearer ' + t;
    return config;
  });

  http.interceptors.response.use(
    (res) => {
      const d = res.data;
      // 文件下载（blob）直接返回，不走业务 code 约定
      if (d instanceof Blob) return d;
      if (d && d.code === 0) return d.data;
      // 后端对鉴权失败统一返回 HTTP 200 + body code=401（见 JwtInterceptor）
      if (d && d.code === 401) {
        localStorage.removeItem('coc_token');
        COC.store.token = '';
        COC.store.user = null;
        if (location.hash !== '#/login') {
          ElementPlus.ElMessage.error((d && d.errorMsg) || '登录已过期，请重新登录');
          location.hash = '#/login';
        }
        return Promise.reject(new Error((d && d.errorMsg) || '登录已过期'));
      }
      ElementPlus.ElMessage.error((d && d.errorMsg) || '请求失败');
      return Promise.reject(new Error((d && d.errorMsg) || '请求失败'));
    },
    (err) => {
      if (err.response && err.response.status === 401) {
        localStorage.removeItem('coc_token');
        ElementPlus.ElMessage.error('登录已过期，请重新登录');
        if (location.hash !== '#/login') location.hash = '#/login';
      } else if (err.response && err.response.data && err.response.data.errorMsg) {
        ElementPlus.ElMessage.error(err.response.data.errorMsg);
      } else {
        ElementPlus.ElMessage.error('网络错误，请确认后端服务已启动');
      }
      return Promise.reject(err);
    }
  );

  const store = reactive({
    token: localStorage.getItem('coc_token') || '',
    user: null,
    menus: [],
    get isLogin() { return !!this.token; },
    get isSuperAdmin() { return !!this.user && !!this.user.superAdmin; },
    get permissions() { return (this.user && this.user.permissions) || []; },
    // 按钮/接口权限完全由菜单里配置的权限标识驱动（user.permissions 来自角色-菜单绑定）。
    // 不再硬编码超级管理员放行：超级管理员的权限同样来自其角色所绑定的菜单，
    // 因此其未绑定的业务按钮自然不可见（即“超级管理员不参与业务”）。
    hasPerm(p) { return this.permissions.includes(p); }
  });

  const api = {
    // 鉴权
    login: (username, password) =>
      http.post('/api/auth/login', { username, password }),
    register: (data) => http.post('/api/auth/register', data),
    info: () => http.get('/api/auth/info'),
    logout: () => http.post('/api/auth/logout'),
    changePassword: (oldPassword, newPassword) =>
      http.post('/api/auth/change-password', { oldPassword, newPassword }),
    assignRole: (userId, roleIds) =>
      http.post('/api/auth/assign-role', { userId, roleIds }),

    // 角色-菜单绑定管理
    menuTree: () => http.get('/api/sys/menu/tree'),
    roleMenus: (roleId) => http.get('/api/sys/role/' + roleId + '/menus'),
    assignMenus: (roleId, menuIds) => http.post('/api/sys/role/' + roleId + '/menus', menuIds),

    // 通用 CRUD
    page: (url, params) => http.get(url + '/page', { params }),
    create: (url, data) => http.post(url, data),
    update: (url, data) => http.put(url, data),
    get: (url, params) => http.get(url, { params }), // 通用 GET，支持查询参数或 url/{id}
    getById: (url, id) => http.get(url + '/' + id),
    remove: (url, id) => http.delete(url + '/' + id),

    // 字典项（用于下拉）
    dictItems: (groupCode) =>
      http.get('/api/dict/item/page', { params: { groupCode, size: 9999 } }),

    // 看板
    dashboardOverview: () => http.get('/api/dashboard/overview'),
    dashboardWarStat: () => http.get('/api/dashboard/war-stat'),
    dashboardLeagueRank: () => http.get('/api/dashboard/league-rank'),

    // 群组成员
    groupMemberPage: (params) => http.get('/api/clan/group/user/page', { params }),
    groupMemberSetAdmin: (userId) => http.put('/api/clan/group/user/' + userId + '/set-admin'),
    groupMemberKick: (userId) => http.put('/api/clan/group/user/' + userId + '/kick'),
    groupMemberCancelAdmin: (userId) => http.put('/api/clan/group/user/' + userId + '/cancel-admin'),

    // 联赛战绩导入
    leagueImportPreview: (formData) => http.post('/api/league/record/import/preview', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
    leagueImportConfirm: (body) => http.post('/api/league/record/import/confirm', body),
    leagueImportTemplate: (type) => http.get('/api/league/record/import/template', { params: { type: type }, responseType: 'blob' }),
    leagueCheckMembers: (body) => http.post('/api/league/record/import/check-members', body),

    // 部落成员 Excel 导入
    clanMemberImportPreview: (formData) => http.post('/api/clan/member/import/preview', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
    clanMemberImportConfirm: (body) => http.post('/api/clan/member/import/confirm', body),
    clanMemberImportTemplate: () => http.get('/api/clan/member/import/template', { responseType: 'blob' }),

    // 一键计算战斗力
    combatPowerConfig: () => http.get('/api/clan/member/combat-power/config'),
    combatPowerCalculate: (body) => http.post('/api/clan/member/combat-power/calculate', body),

    // 入组申请
    applyCreate: (body) => http.post('/api/clan/group/apply', body),
    applyPage: (params) => http.get('/api/clan/group/apply/page', { params }),
    applyApprove: (id) => http.put('/api/clan/group/apply/' + id + '/approve'),
    applyReject: (id) => http.put('/api/clan/group/apply/' + id + '/reject'),
    applyDelete: (id) => http.delete('/api/clan/group/apply/' + id),

    // 联赛快速报名（公开接口，无需登录；由 quick-signup.html 独立页面调用）
    quickGroup: (groupNo) => http.get('/api/quick/groups/' + encodeURIComponent(groupNo)),
    quickClans: (groupNo) => http.get('/api/quick/clans', { params: { groupNo } }),
    quickLeagues: (groupNo) => http.get('/api/quick/leagues', { params: { groupNo } }),
    quickSignups: (params) => http.get('/api/quick/signups', { params }),
    quickSubmit: (body) => http.post('/api/quick/signup', body)
  };

  // 全局字典分组 / 角色选项（登录后填充，供下拉使用）
  window.COC = { API_BASE, http, store, api, dictGroups: [], roles: [] };
})();
