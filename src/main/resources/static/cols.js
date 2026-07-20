/* 列配置：所有 CRUD 表格列 + 表单字段配置
 * 依赖：无（仅定义数据，createCrud 在 crud-instances.js 中使用）
 * 暴露到 window.COC_COLS 上，避免污染全局
 */
(function () {
  const req = (msg) => [{ required: true, message: msg, trigger: 'blur' }];

  const clanCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'clanName', label: '部落名称', search: true, rule: req('请输入部落名称') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'intro', label: '简介', type: 'textarea' }
  ];
  const memberCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'memberName', label: '成员名称', search: true, rule: req('请输入成员名称') },
    { prop: 'memberNo', label: '成员编号', rule: req('请输入成员编号') },
    { prop: 'clanNo', label: '部落编号', search: true },
    { prop: 'warStatus', label: '参战状态', type: 'switch', activeText: '参战', inactiveText: '不参战' },
    { prop: 'intro', label: '简介', type: 'textarea' }
  ];
  const warCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'warNo', label: '部落战编号', search: true, rule: req('请输入部落战编号') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'winStatus', label: '胜负', type: 'switch', activeText: '胜', inactiveText: '负' },
    { prop: 'startTime', label: '开始时间', type: 'date' },
    { prop: 'intro', label: '描述', type: 'textarea' }
  ];
  const warRecordCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'warNo', label: '部落战编号', search: true, rule: req('请输入部落战编号') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'memberName', label: '成员名称', rule: req('请输入成员名称') },
    { prop: 'memberNo', label: '成员编号', rule: req('请输入成员编号') },
    { prop: 'atk1Stars', label: '一攻星数', type: 'number' },
    { prop: 'atk1Rate', label: '一攻百分比', type: 'number' },
    { prop: 'atk2Stars', label: '二攻星数', type: 'number' },
    { prop: 'atk2Rate', label: '二攻百分比', type: 'number' },
    { prop: 'actualAttacks', label: '实际攻击次数', type: 'number' }
  ];
  const leagueCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'leagueName', label: '联赛名称', search: true, rule: req('请输入联赛名称') },
    { prop: 'leagueNo', label: '联赛编号', search: true, rule: req('请输入联赛编号') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'signupStart', label: '报名开始', type: 'date' },
    { prop: 'signupEnd', label: '报名结束', type: 'date' },
    { prop: 'tier', label: '联赛等级', type: 'number' },
    { prop: 'resultRank', label: '本段排名', type: 'number' },
    { prop: 'extraCount', label: '额外人数', type: 'number' },
    { prop: 'leagueCoin', label: '联赛币', type: 'number' },
    { prop: 'extraCoin', label: '额外币', type: 'number' },
    { prop: 'promoteStatus', label: '升降级', type: 'select', default: 0, options: [{ label: '无', value: 0 }, { label: '晋升', value: 1 }, { label: '降级', value: 2 }] },
    { prop: 'intro', label: '简介', type: 'textarea' }
  ];
  const leagueRecordCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'leagueNo', label: '联赛编号', search: true, rule: req('请输入联赛编号') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'memberName', label: '成员名称', rule: req('请输入成员名称') },
    { prop: 'memberNo', label: '成员编号', rule: req('请输入成员编号') },
    { prop: 'winStars', label: '胜利之星', type: 'number' },
    { prop: 'destroyRate', label: '摧毁率(%)', type: 'number' },
    { prop: 'actualAttacks', label: '实际攻击', type: 'number' },
    { prop: 'requiredAttacks', label: '要求攻击', type: 'number' },
    { prop: 'hasExtra', label: '额外参赛', type: 'select', default: 0, options: [{ label: '否', value: 0 }, { label: '是', value: 1 }] }
  ];
  const leagueSignupCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'leagueNo', label: '联赛编号', search: true, rule: req('请输入联赛编号') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'memberName', label: '成员名称', rule: req('请输入成员名称') },
    { prop: 'memberNo', label: '成员编号', rule: req('请输入成员编号') },
    { prop: 'signupStatus', label: '报名状态', type: 'select', default: 1, options: [{ label: '取消', value: 0 }, { label: '报名', value: 1 }] },
    { prop: 'signupTime', label: '报名时间', type: 'date' }
  ];
  const groupCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'groupNo', label: '群组编号', search: true, rule: req('请输入群组编号') },
    { prop: 'groupName', label: '群组名称', search: true, rule: req('请输入群组名称') },
    // 表格列：直接展示后端关联返回的 ownerName（只读，仅表格显示）
    { prop: 'ownerName', label: '群主', hideInForm: true },
    // 表单字段：用远程下拉选择用户，提交时传 ownerId（仅表单显示）
    { prop: 'ownerId', label: '群主', type: 'remote-select', hideInTable: true,
      url: '/api/sys/user', labelKey: 'username', valueKey: 'id',
      placeholder: '请输入用户名搜索选择群主（可留空）' },
    { prop: 'intro', label: '简介', type: 'textarea' },
    { prop: 'status', label: '状态', type: 'switch', activeText: '启用', inactiveText: '禁用' }
  ];
  const menuCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'menuName', label: '菜单名称', search: true, rule: req('请输入菜单名称') },
    { prop: 'menuType', label: '类型', type: 'select', default: 1, options: [{ label: '目录', value: 0 }, { label: '菜单', value: 1 }, { label: '按钮', value: 2 }] },
    { prop: 'parentId', label: '父菜单', type: 'tree-select', default: 0,
      url: '/api/sys/menu',
      placeholder: '不选则为顶级菜单' },
    { prop: 'path', label: '路径' },
    { prop: 'component', label: '组件' },
    { prop: 'icon', label: '图标' },
    { prop: 'permission', label: '权限标识' },
    { prop: 'sort', label: '排序', type: 'number' }
  ];
  const dictGroupCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'groupCode', label: '分组编码', search: true, rule: req('请输入分组编码') },
    { prop: 'groupName', label: '分组名称', search: true, rule: req('请输入分组名称') },
    { prop: 'remark', label: '备注', type: 'textarea' },
    { prop: 'status', label: '状态', type: 'switch', activeText: '启用', inactiveText: '禁用' }
  ];
  const dictItemCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'groupCode', label: '所属分组', type: 'select', rule: req('请选择分组'), options: () => COC.dictGroups || [] },
    { prop: 'itemName', label: '字典名称', search: true, rule: req('请输入字典名称') },
    { prop: 'itemValue', label: '字典值', search: true, rule: req('请输入字典值') },
    { prop: 'sort', label: '排序', type: 'number' },
    { prop: 'status', label: '状态', type: 'switch', activeText: '启用', inactiveText: '禁用' }
  ];

  // 暴露到全局，供 crud-instances.js 使用
  window.COC_COLS = {
    req,
    clanCols, memberCols,
    warCols, warRecordCols,
    leagueCols, leagueRecordCols, leagueSignupCols,
    groupCols, menuCols,
    dictGroupCols, dictItemCols
  };
})();
