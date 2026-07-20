/* 通用 CRUD 组件实例：所有 createCrud({...}) 调用
 * 依赖顺序：cols.js → crud.js → crud-instances.js → app.js
 * 暴露到 window.COC_CRUD 上，供 app.js 使用
 */
(function () {
  const { clanCols, memberCols, warCols, warRecordCols,
    leagueCols, leagueRecordCols, leagueSignupCols,
    groupCols, menuCols, dictGroupCols, dictItemCols } = window.COC_COLS;

  const clanCrud = createCrud({ name: 'ClanCrud', baseUrl: '/api/clan', cols: clanCols });
  const memberCrud = createCrud({ name: 'MemberCrud', baseUrl: '/api/clan/member', cols: memberCols });
  const warCrud = createCrud({ name: 'WarCrud', baseUrl: '/api/war', cols: warCols });
  const warRecordCrud = createCrud({ name: 'WarRecordCrud', baseUrl: '/api/war/record', cols: warRecordCols });
  const leagueCrud = createCrud({ name: 'LeagueCrud', baseUrl: '/api/league', cols: leagueCols });
  const leagueRecordCrud = createCrud({ name: 'LeagueRecordCrud', baseUrl: '/api/league/record', cols: leagueRecordCols });
  const leagueSignupCrud = createCrud({
    name: 'LeagueSignupCrud',
    baseUrl: '/api/league/signup',
    listMode: true,
    lazy: true, // 需先选联赛才加载
    cols: leagueSignupCols
  });
  const groupCrud = createCrud({ name: 'GroupCrud', baseUrl: '/api/clan/group', cols: groupCols });
  const menuCrud = createCrud({ name: 'MenuCrud', baseUrl: '/api/sys/menu', cols: menuCols });
  const dictGroupCrud = createCrud({ name: 'DictGroupCrud', baseUrl: '/api/dict/group', cols: dictGroupCols });
  const dictItemCrud = createCrud({ name: 'DictItemCrud', baseUrl: '/api/dict/item', cols: dictItemCols });

  // 暴露到全局，供 app.js 使用
  window.COC_CRUD = {
    clanCrud, memberCrud,
    warCrud, warRecordCrud,
    leagueCrud, leagueRecordCrud, leagueSignupCrud,
    groupCrud, menuCrud,
    dictGroupCrud, dictItemCrud
  };
})();
