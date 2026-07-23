/**
 * api.js 测试：验证全局 store 状态、响应拦截器、API 封装。
 */
var { loadAllScripts } = require('../helpers/load-scripts');

describe('api.js - 前端接口层', function () {

  beforeEach(function () {
    loadAllScripts({ configEnv: 'dev' });
  });

  // ==================== store 状态 ====================

  describe('COC.store - 登录状态', function () {

    test('未登录时 isLogin 为 false', function () {
      expect(COC.store.isLogin).toBe(false);
      expect(COC.store.token).toBe('');
    });

    test('设置 token 后 isLogin 为 true', function () {
      COC.store.token = 'mock-jwt-token';
      expect(COC.store.isLogin).toBe(true);
    });

    test('未登录时 isSuperAdmin 为 false', function () {
      expect(COC.store.isSuperAdmin).toBe(false);
    });

    test('superAdmin=true 的用户 isSuperAdmin 为 true', function () {
      COC.store.user = { superAdmin: true };
      expect(COC.store.isSuperAdmin).toBe(true);
    });

    test('无权限时 permissions 为空数组', function () {
      expect(COC.store.permissions).toEqual([]);
    });

    test('有用户时 permissions 从 user.permissions 获取', function () {
      COC.store.user = { permissions: ['system:manage', 'clan:edit'] };
      expect(COC.store.permissions).toContain('system:manage');
      expect(COC.store.permissions).toContain('clan:edit');
    });

    test('hasPerm - 超级管理员拥有所有权限', function () {
      COC.store.user = { superAdmin: true, permissions: [] };
      expect(COC.store.hasPerm('system:manage')).toBe(true);
      expect(COC.store.hasPerm('anything')).toBe(true);
    });

    test('hasPerm - 普通用户仅拥有已授权的权限', function () {
      COC.store.user = { superAdmin: false, permissions: ['clan:edit'] };
      expect(COC.store.hasPerm('clan:edit')).toBe(true);
      expect(COC.store.hasPerm('system:manage')).toBe(false);
    });

    test('hasPerm - 无用户时返回 false', function () {
      COC.store.user = null;
      expect(COC.store.hasPerm('system:manage')).toBe(false);
    });
  });

  // ==================== API 封装 ====================

  describe('COC.api - 接口封装', function () {

    test('login 调用 POST /api/auth/login', function () {
      var spy = jest.spyOn(COC.http, 'post').mockResolvedValue({});
      COC.api.login('admin', '123456');
      expect(spy).toHaveBeenCalledWith('/api/auth/login', { username: 'admin', password: '123456' });
      spy.mockRestore();
    });

    test('register 调用 POST /api/auth/register', function () {
      var spy = jest.spyOn(COC.http, 'post').mockResolvedValue({});
      var data = { username: 'new', password: 'pwd', nickname: '新用户' };
      COC.api.register(data);
      expect(spy).toHaveBeenCalledWith('/api/auth/register', data);
      spy.mockRestore();
    });

    test('info 调用 GET /api/auth/info', function () {
      var spy = jest.spyOn(COC.http, 'get').mockResolvedValue({});
      COC.api.info();
      expect(spy).toHaveBeenCalledWith('/api/auth/info');
      spy.mockRestore();
    });

    test('page 调用 GET {url}/page 带参数', function () {
      var spy = jest.spyOn(COC.http, 'get').mockResolvedValue({});
      COC.api.page('/api/clan', { keyword: 'test', current: 1 });
      expect(spy).toHaveBeenCalledWith('/api/clan/page', { params: { keyword: 'test', current: 1 } });
      spy.mockRestore();
    });

    test('create 调用 POST {url}', function () {
      var spy = jest.spyOn(COC.http, 'post').mockResolvedValue({});
      var data = { name: 'test' };
      COC.api.create('/api/clan', data);
      expect(spy).toHaveBeenCalledWith('/api/clan', data);
      spy.mockRestore();
    });

    test('update 调用 PUT {url}', function () {
      var spy = jest.spyOn(COC.http, 'put').mockResolvedValue({});
      var data = { id: 1, name: 'updated' };
      COC.api.update('/api/clan', data);
      expect(spy).toHaveBeenCalledWith('/api/clan', data);
      spy.mockRestore();
    });

    test('remove 调用 DELETE {url}/{id}', function () {
      var spy = jest.spyOn(COC.http, 'delete').mockResolvedValue({});
      COC.api.remove('/api/clan', 42);
      expect(spy).toHaveBeenCalledWith('/api/clan/42');
      spy.mockRestore();
    });

    test('dictItems 请求字典项带 size=9999', function () {
      var spy = jest.spyOn(COC.http, 'get').mockResolvedValue({});
      COC.api.dictItems('war_status');
      expect(spy).toHaveBeenCalledWith('/api/dict/item/page', { params: { groupCode: 'war_status', size: 9999 } });
      spy.mockRestore();
    });
  });

  // ==================== 响应拦截器 ====================

  describe('响应拦截器', function () {

    test('code=0 时返回 data', async function () {
      var interceptor = global.__test.responseInterceptors[0];
      var mockResponse = { data: { code: 0, data: { id: 1, name: 'test' } } };
      var result = await interceptor.onFulfilled(mockResponse);
      expect(result).toEqual({ id: 1, name: 'test' });
    });

    test('code=401 时清除 token 并 reject', async function () {
      // 先设置 token 模拟已登录
      localStorage.setItem('coc_token', 'old-token');
      COC.store.token = 'old-token';

      var interceptor = global.__test.responseInterceptors[0];
      var mockResponse = { data: { code: 401, errorMsg: '登录已过期' } };

      await expect(interceptor.onFulfilled(mockResponse)).rejects.toThrow('登录已过期');
      expect(localStorage.getItem('coc_token')).toBeNull();
      expect(COC.store.token).toBe('');
      expect(COC.store.user).toBeNull();
    });

    test('code=非0非401 时 reject 并提示错误', async function () {
      var interceptor = global.__test.responseInterceptors[0];
      var mockResponse = { data: { code: 500, errorMsg: '服务器错误' } };

      await expect(interceptor.onFulfilled(mockResponse)).rejects.toThrow('服务器错误');
    });

    test('网络错误时 reject', async function () {
      var interceptor = global.__test.responseInterceptors[0];
      var networkError = new Error('Network Error');
      networkError.response = null;

      await expect(interceptor.onRejected(networkError)).rejects.toBe(networkError);
    });

    test('HTTP 401 时 reject 并清除 token', async function () {
      localStorage.setItem('coc_token', 'old-token');
      var interceptor = global.__test.responseInterceptors[0];
      var httpError = {
        response: { status: 401, data: { errorMsg: '未登录' } }
      };

      await expect(interceptor.onRejected(httpError)).rejects.toBe(httpError);
      expect(localStorage.getItem('coc_token')).toBeNull();
    });
  });

  // ==================== 请求拦截器 ====================

  describe('请求拦截器', function () {

    test('有 token 时添加 Authorization 头', function () {
      localStorage.setItem('coc_token', 'my-jwt-token');
      var interceptor = global.__test.requestInterceptors[0];
      var config = { headers: {} };
      var result = interceptor(config);
      expect(result.headers.Authorization).toBe('Bearer my-jwt-token');
    });

    test('无 token 时不添加 Authorization 头', function () {
      var interceptor = global.__test.requestInterceptors[0];
      var config = { headers: {} };
      var result = interceptor(config);
      expect(result.headers.Authorization).toBeUndefined();
    });
  });

  // ==================== getApiBase 逻辑 ====================

  describe('API_BASE - 后端地址解析', function () {

    test('开发环境使用 APP_CONFIG.apiUrl', function () {
      loadAllScripts({ configEnv: 'dev' });
      expect(COC.API_BASE).toBe('http://localhost:8080');
    });

    test('生产环境空 apiUrl 时使用 location.origin', function () {
      loadAllScripts({ configEnv: 'prod' });
      // prod 配置中 apiUrl 为 ''，应回退到 location.origin
      expect(COC.API_BASE).toBe(window.location.origin);
    });
  });
});
