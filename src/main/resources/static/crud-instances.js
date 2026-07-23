/* 通用 CRUD 组件实例：所有 createCrud({...}) 调用
 * 依赖顺序：cols.js → crud.js → crud-instances.js → app.js
 * 暴露到 window.COC_CRUD 上，供 app.js 使用
 */
(function () {
  const { clanCols, memberCols, warCols, warRecordCols,
    leagueCols, leagueClanScoreCols, leagueRecordCols, leagueSignupCols,
    groupCols, menuCols, dictGroupCols, dictItemCols } = window.COC_COLS;

  const clanCrud = createCrud({ name: 'ClanCrud', baseUrl: '/api/clan', cols: clanCols });
  // 部落新增：groupNo 自动从当前登录用户取（群主/部落管理员有 groupNo）；
  // 若为空（如超级管理员直接新增），提示需成为群主或部落管理员。
  const _clanOpenCreate = clanCrud.methods.openCreate;
  clanCrud.methods.openCreate = function () {
    var groupNo = (COC.store.user && COC.store.user.groupNo) || '';
    if (!groupNo) {
      if (window.ElementPlus && ElementPlus.ElMessage) {
        ElementPlus.ElMessage.warning('需成为群主或部落管理员才能新增部落');
      }
      return;
    }
    _clanOpenCreate.call(this);
    this.form.groupNo = groupNo;
  };
  const memberCrud = createCrud({ name: 'MemberCrud', baseUrl: '/api/clan/member', cols: memberCols });
  const warCrud = createCrud({ name: 'WarCrud', baseUrl: '/api/war', cols: warCols });
  const warRecordCrud = createCrud({ name: 'WarRecordCrud', baseUrl: '/api/war/record', cols: warRecordCols });
  const leagueCrud = createCrud({ name: 'LeagueCrud', baseUrl: '/api/league', cols: leagueCols });
  const leagueClanScoreCrud = createCrud({ name: 'LeagueClanScoreCrud', baseUrl: '/api/league/score', cols: leagueClanScoreCols });
  // 联赛新增：自动填入联赛名称和编号
  // leagueName 格式 "yyyy年M月联赛"，leagueNo 格式 "yyyyMMdd"
  // 日期规则：
  //   日 > 20：月份 +1，日期设为 01（如 7月23日 → 8月01日）
  //   10 <= 日 <= 20：日期设为 15（如 7月15日 → 7月15日）
  //   日 < 10：日期设为 01（如 7月5日 → 7月01日）
  const _leagueOpenCreate = leagueCrud.methods.openCreate;
  leagueCrud.methods.openCreate = function () {
    _leagueOpenCreate.call(this);
    var now = new Date();
    var year = now.getFullYear();
    var month = now.getMonth() + 1; // JS 月份从 0 开始
    var day = now.getDate();
    var dd;
    if (day > 20) {
      month++;
      if (month > 12) { month = 1; year++; }
      dd = '01';
    } else if (day >= 10) {
      dd = '15';
    } else {
      dd = '01';
    }
    var mm = month < 10 ? '0' + month : '' + month;
    this.form.leagueName = year + '年' + month + '月联赛';
    this.form.leagueNo = '' + year + mm + dd;
    // 报名开始：当前时间；报名结束：当前时间 +5 天
    var fmt = function (d) {
      var y = d.getFullYear();
      var mo = d.getMonth() + 1;
      var da = d.getDate();
      var h = d.getHours();
      var mi = d.getMinutes();
      var s = d.getSeconds();
      var p = function (n) { return n < 10 ? '0' + n : '' + n; };
      return y + '-' + p(mo) + '-' + p(da) + ' ' + p(h) + ':' + p(mi) + ':' + p(s);
    };
    this.form.signupStart = fmt(now);
    var end = new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000);
    this.form.signupEnd = fmt(end);
  };
  const leagueRecordCrud = createCrud({ name: 'LeagueRecordCrud', baseUrl: '/api/league/record', cols: leagueRecordCols });
  const leagueSignupCrud = createCrud({
    name: 'LeagueSignupCrud',
    baseUrl: '/api/league/signup',
    cols: leagueSignupCols,
    extraButtons: [
      { text: '一键初始化报名', type: 'warning', click: 'initSignup' }
    ]
  });
  // 首次加载时自动选最新联赛（id 最大 = 最新），设入搜索栏后查询该联赛报名数据
  var _signupOriginalLoad = leagueSignupCrud.methods.load;
  leagueSignupCrud.methods.load = async function () {
    if (!this._autoLeaguePicked) {
      this._autoLeaguePicked = true;
      // remoteOptions.leagueNo 由 preloadRemoteOptions() 在 mounted 中预加载，
      // BaseCrudController.page 默认按 id DESC 排序，第一条即最新联赛
      var leagues = this.remoteOptions.leagueNo || [];
      if (leagues.length > 0 && !(this.filters && this.filters.leagueNo)) {
        this.filters = this.filters || {};
        this.filters.leagueNo = leagues[0].value;
      }
    }
    await _signupOriginalLoad.call(this);
  };
  // 一键初始化报名：弹窗选部落 → 调 /init 接口 → 重新加载列表
  leagueSignupCrud.methods.initSignup = function () {
    this.initLeagueNo = '';
    this.initClanNo = '';
    this.initDialogVisible = true;
  };
  leagueSignupCrud.methods.doInitSignup = async function () {
    if (!this.initLeagueNo) {
      if (window.ElementPlus && ElementPlus.ElMessage) {
        ElementPlus.ElMessage.warning('请选择联赛');
      }
      return;
    }
    if (!this.initClanNo) {
      if (window.ElementPlus && ElementPlus.ElMessage) {
        ElementPlus.ElMessage.warning('请选择部落');
      }
      return;
    }
    this.initLoading = true;
    try {
      var result = await COC.http.post('/api/league/signup/init', null, {
        params: { leagueNo: this.initLeagueNo, clanNo: this.initClanNo }
      });
      var data = result || {};
      if (window.ElementPlus && ElementPlus.ElMessage) {
        var msg = '初始化完成：新增 ' + (data.inserted||0) + ' 条，替换 ' + (data.replaced||0) + ' 条，跳过 ' + (data.skipped||0) + ' 条';
        ElementPlus.ElMessage.success(msg);
      }
      this.initDialogVisible = false;
      // 初始化后自动筛选当前联赛
      this.filters = this.filters || {};
      this.filters.leagueNo = this.initLeagueNo;
      await this.load();
    } catch (e) {
      if (window.ElementPlus && ElementPlus.ElMessage) {
        ElementPlus.ElMessage.error('初始化失败：' + ((e && e.message) || ''));
      }
    } finally {
      this.initLoading = false;
    }
  };
  const groupCrud = createCrud({ name: 'GroupCrud', baseUrl: '/api/clan/group', cols: groupCols });
  // 菜单管理：使用专用树视图组件（替代通用 CRUD 列表），更适合层级数据维护。
  const menuTree = window.createMenuTree();
  const dictGroupCrud = createCrud({ name: 'DictGroupCrud', baseUrl: '/api/dict/group', cols: dictGroupCols });
  const dictItemCrud = createCrud({ name: 'DictItemCrud', baseUrl: '/api/dict/item', cols: dictItemCols });

  // 暴露到全局，供 app.js 使用
  window.COC_CRUD = {
    clanCrud, memberCrud,
    warCrud, warRecordCrud,
    leagueCrud, leagueClanScoreCrud, leagueRecordCrud, leagueSignupCrud,
    groupCrud, menuTree,
    dictGroupCrud, dictItemCrud
  };
})();
