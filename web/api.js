/* 部落冲突部族联赛管理系统 — 前端接口层、鉴权状态、通用 API 封装 */
(function () {
  const { reactive } = Vue;

  // 支持通过 ?api=http://host:port 覆盖后端地址；默认指向本地后端 8080
  const params = new URLSearchParams(location.search);
  const API_BASE = params.get('api') || 'http://localhost:8080';

  const http = axios.create({ baseURL: API_BASE, timeout: 20000 });

  http.interceptors.request.use((config) => {
    const t = localStorage.getItem('coc_token');
    if (t) config.headers.Authorization = 'Bearer ' + t;
    return config;
  });

  http.interceptors.response.use(
    (res) => {
      const d = res.data;
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
    hasPerm(p) { return this.isSuperAdmin || this.permissions.includes(p); }
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

    // 通用 CRUD
    page: (url, params) => http.get(url + '/page', { params }),
    create: (url, data) => http.post(url, data),
    update: (url, data) => http.put(url, data),
    get: (url, params) => http.get(url, { params }), // 通用 GET，支持查询参数或 url/{id}
    getById: (url, id) => http.get(url + '/' + id),
    remove: (url, id) => http.delete(url + '/' + id),

    // 字典项（用于下拉）
    dictItems: (groupCode) =>
      http.get('/api/dict/item', { params: { groupCode, size: 9999 } }),

    // 看板
    dashboardOverview: () => http.get('/api/dashboard/overview'),
    dashboardWarStat: () => http.get('/api/dashboard/war-stat'),
    dashboardLeagueRank: () => http.get('/api/dashboard/league-rank')
  };

  // 全局字典分组 / 角色选项（登录后填充，供下拉使用）
  window.COC = { API_BASE, http, store, api, dictGroups: [], roles: [] };
})();
