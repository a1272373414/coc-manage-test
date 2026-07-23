/**
 * 脚本加载辅助：在 Jest/jsdom 环境中按顺序加载前端源代码（IIFE 全局脚本）。
 * 使用间接 eval 在全局作用域执行，使脚本能设置 window 全局变量。
 */
var fs = require('fs');
var path = require('path');

var STATIC_DIR = path.resolve(__dirname, '..', '..', 'src', 'main', 'resources', 'static');

/**
 * 加载单个脚本文件到全局作用域
 * @param {string} filename - static 目录下的文件名
 */
function loadScript(filename) {
  var src = fs.readFileSync(path.join(STATIC_DIR, filename), 'utf8');
  // 间接 eval：在全局作用域执行，使 IIFE 中的 window.xxx = ... 生效
  // eslint-disable-next-line no-eval
  (0, eval)(src);
}

/**
 * 按依赖顺序加载全部前端脚本
 * 顺序：config → api → cols → crud → crud-instances → app
 * @param {object} opts - 可选配置
 * @param {string} opts.configEnv - 'dev' 或 'prod'，默认 'dev'
 * @param {boolean} opts.loadApp - 是否加载 app.js（默认 false，app.js 会创建 Vue 实例）
 */
function loadAllScripts(opts) {
  opts = opts || {};
  var env = opts.configEnv || 'dev';
  var loadApp = opts.loadApp || false;

  // 重置全局状态（避免多次加载累积）
  delete global.COC;
  delete global.COC_COLS;
  delete global.COC_CRUD;
  delete global.createCrud;
  delete global.APP_CONFIG;
  // 重置 localStorage
  global.localStorage.clear();

  // 按依赖顺序加载
  loadScript('config.' + env + '.js');
  loadScript('api.js');
  loadScript('cols.js');
  loadScript('crud.js');
  loadScript('crud-instances.js');
  if (loadApp) loadScript('app.js');
}

module.exports = { loadScript: loadScript, loadAllScripts: loadAllScripts, STATIC_DIR: STATIC_DIR };
