/**
 * Jest 全局 mock 设置：
 * 在 jsdom 环境中模拟前端脚本所需的 Vue、axios、ElementPlus 等全局依赖。
 * 源代码以 IIFE 全局脚本模式编写（window.COC / Vue / axios），
 * 这里提供最小化的 mock 使脚本能加载并暴露可测试的逻辑。
 */

// ============ Mock Vue ============
global.Vue = {
  reactive: function (obj) { return obj; },
  ref: function (v) { return { value: v }; },
  computed: function (fn) {
    return { get value() { return fn(); } };
  },
  createApp: function () {
    return {
      use: function () { return this; },
      component: function () { return this; },
      mount: function () {}
    };
  },
  onMounted: function () {},
  nextTick: function () { return Promise.resolve(); }
};

// ============ Mock VueRouter ============
global.VueRouter = {
  createRouter: function () {
    return { beforeEach: function () {} };
  },
  createWebHashHistory: function () { return {}; }
};

// ============ Mock ElementPlus ============
global.ElementPlus = {
  ElMessage: {
    success: function () {},
    error: function () {},
    warning: function () {}
  },
  ElMessageBox: {
    confirm: function () { return Promise.resolve(); }
  }
};

// ============ Mock ElementPlusIconsVue ============
global.ElementPlusIconsVue = {};

// ============ Mock ECharts ============
global.echarts = {
  init: function () {
    return { setOption: function () {}, dispose: function () {} };
  }
};

// ============ Mock Axios ============
// 拦截器回调会被脚本注册，我们保存它们以便测试时调用
var _requestInterceptors = [];
var _responseInterceptors = [];

var _mockHttp = {
  get: function () { return Promise.resolve({}); },
  post: function () { return Promise.resolve({}); },
  put: function () { return Promise.resolve({}); },
  delete: function () { return Promise.resolve({}); },
  interceptors: {
    request: {
      use: function (onFulfilled) {
        _requestInterceptors.push(onFulfilled);
      }
    },
    response: {
      use: function (onFulfilled, onRejected) {
        _responseInterceptors.push({ onFulfilled: onFulfilled, onRejected: onRejected });
      }
    }
  }
};

global.axios = {
  create: function () { return _mockHttp; }
};

// 暴露拦截器引用，供测试访问
global.__test = {
  http: _mockHttp,
  requestInterceptors: _requestInterceptors,
  responseInterceptors: _responseInterceptors,
  setHttpMock: function (impl) {
    Object.assign(_mockHttp, impl);
  }
};
