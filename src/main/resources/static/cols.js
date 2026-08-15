/* 列配置：所有 CRUD 表格列 + 表单字段配置
 * 依赖：无（仅定义数据，createCrud 在 crud-instances.js 中使用）
 * 暴露到 window.COC_COLS 上，避免污染全局
 */
(function () {
  const req = (msg) => [{ required: true, message: msg, trigger: "blur" }];

  const clanCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "clanName",
      label: "部落名称",
      search: true,
      rule: req("请输入部落名称"),
    },
    {
      prop: "clanNo",
      label: "部落编号",
      search: true,
      rule: req("请输入部落编号"),
    },
    // 群组编号：新增时自动从当前用户取，表单只读，表格不展示
    { prop: "groupNo", label: "群组编号", hideInTable: true, disabled: true },
    { prop: "intro", label: "简介", type: "textarea" },
    {
      prop: "sort",
      label: "排序",
      type: "number",
      rule: req("请输入排序号"),
      default: 0,
    },
  ];
  const memberCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "memberName",
      label: "成员名称",
      search: true,
      sortable: true,
      rule: req("请输入成员名称"),
    },
    // 表格中合并展示的虚拟列：表单里隐藏，由下面 5 个独立字段组成
    {
      prop: "backupNames",
      label: "备用名称",
      hideInForm: true,
      formatter: (row) =>
        [
          row.backupName1,
          row.backupName2,
          row.backupName3,
          row.backupName4,
          row.backupName5,
        ]
          .filter((x) => x != null && x !== "")
          .join("、") || "-",
    },
    // 备用名称拆成 5 个独立字段，仅出现在新增/编辑表单（表格隐藏）
    { prop: "backupName1", label: "备用名称1", hideInTable: true },
    { prop: "backupName2", label: "备用名称2", hideInTable: true },
    { prop: "backupName3", label: "备用名称3", hideInTable: true },
    { prop: "backupName4", label: "备用名称4", hideInTable: true },
    { prop: "backupName5", label: "备用名称5", hideInTable: true },
    { prop: "memberNo", label: "成员编号" },
    // 部落编号：远程下拉选择，从 /api/clan 接口拉取部落列表（label=clanName, value=clanNo）
    {
      prop: "clanNo",
      label: "所属部落",
      search: true,
      type: "remote-select",
      rule: req("请选择所属部落"),
      url: "/api/clan",
      labelKey: "clanName",
      valueKey: "clanNo",
      placeholder: "请输入关键字筛选部落",
    },
    {
      prop: "memberStatus",
      label: "在组状态",
      type: "select",
      search: true,
      rule: req("请选择在组状态"),
      options: [
        { label: "已加入", value: 1 },
        { label: "已退出", value: 0 },
      ],
      default: 1,
    },
    {
      prop: "warStatus",
      label: "默认参战状态",
      type: "switch",
      search: true,
      default: 1,
    },
    {
      prop: "thLevel",
      label: "大本等级",
      type: "number",
      search: true,
      sortable: true,
    },
    { prop: "matchValue", label: "匹配值", type: "number", sortable: true },
    { prop: "combatPower", label: "战斗力", type: "number", sortable: true },
    { prop: "intro", label: "简介", type: "textarea" },
  ];
  const warCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "warNo",
      label: "部落战编号",
      search: true,
      rule: req("请输入部落战编号"),
    },
    {
      prop: "clanNo",
      label: "部落编号",
      search: true,
      rule: req("请输入部落编号"),
    },
    {
      prop: "winStatus",
      label: "胜负",
      type: "switch",
      activeText: "胜",
      inactiveText: "负",
    },
    { prop: "startTime", label: "开始时间", type: "date" },
    { prop: "intro", label: "描述", type: "textarea" },
  ];
  const warRecordCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "warNo",
      label: "部落战编号",
      search: true,
      rule: req("请输入部落战编号"),
    },
    {
      prop: "clanNo",
      label: "部落编号",
      search: true,
      rule: req("请输入部落编号"),
    },
    { prop: "memberName", label: "成员名称", search: true, rule: req("请输入成员名称") },
    { prop: "memberNo", label: "成员编号", rule: req("请输入成员编号") },
    { prop: "atk1Stars", label: "一攻星数", type: "number" },
    { prop: "atk1Rate", label: "一攻百分比", type: "number" },
    { prop: "atk2Stars", label: "二攻星数", type: "number" },
    { prop: "atk2Rate", label: "二攻百分比", type: "number" },
    { prop: "actualAttacks", label: "实际攻击次数", type: "number" },
  ];
  const leagueCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "leagueName",
      label: "联赛名称",
      search: true,
      rule: req("请输入联赛名称"),
    },
    {
      prop: "leagueNo",
      label: "联赛编号",
      search: true,
      type: "date-ymd",
      rule: req("请选择联赛编号"),
      disabledOnEdit: true,
    },
    { prop: "signupStart", label: "报名开始", type: "date" },
    { prop: "signupEnd", label: "报名结束", type: "date" },
  ];
  // 联赛部落成绩（从 league 表拆分）
  const leagueClanScoreCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "leagueNo",
      label: "联赛",
      search: true,
      type: "remote-select",
      hideInTable: true,
      rule: req("请选择联赛"),
      url: "/api/league",
      labelKey: "leagueName",
      valueKey: "leagueNo",
      placeholder: "请输入关键字筛选联赛",
    },
    {
      prop: "clanNo",
      label: "部落",
      search: true,
      type: "remote-select",
      hideInTable: true,
      rule: req("请选择部落"),
      url: "/api/clan",
      labelKey: "clanName",
      valueKey: "clanNo",
      placeholder: "请输入关键字筛选部落",
    },
    { prop: "leagueName", label: "联赛名称", hideInForm: true },
    { prop: "clanName", label: "部落名称", hideInForm: true },
    {
      prop: "tier",
      label: "联赛段位",
      type: "select",
      dictCode: "league_tier",
      search: true,
    },
    { prop: "resultRank", label: "本段排名", type: "number" },
    { prop: "extraCount", label: "额外人数", type: "number" },
    { prop: "leagueCoin", label: "联赛币", type: "number" },
    { prop: "extraCoin", label: "额外币", type: "number" },
    {
      prop: "promoteStatus",
      label: "升降级",
      type: "select",
      default: 0,
      search: true,
      options: [
        { label: "无", value: 0 },
        { label: "晋级", value: 1 },
        { label: "降级", value: 2 },
      ],
    },
  ];
  const leagueRecordCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "leagueNo",
      label: "联赛",
      search: true,
      type: "remote-select",
      hideInTable: true,
      rule: req("请选择联赛"),
      url: "/api/league",
      labelKey: "leagueName",
      valueKey: "leagueNo",
      placeholder: "请输入关键字筛选联赛",
    },
    {
      prop: "clanNo",
      label: "部落",
      search: true,
      type: "remote-select",
      hideInTable: true,
      rule: req("请选择部落"),
      url: "/api/clan",
      labelKey: "clanName",
      valueKey: "clanNo",
      placeholder: "请输入关键字筛选部落",
    },
    {
      prop: "leagueName",
      label: "联赛",
      extraProp: "leagueNo",
      hideInForm: true,
    },
    { prop: "clanName", label: "部落", extraProp: "clanNo", hideInForm: true },
    {
      prop: "memberName",
      label: "成员名称",
      search: true,
      extraProp: "memberNo",
      rule: req("请输入成员名称"),
    },
    { prop: "memberNo", label: "成员编号", hideInTable: true },
    { prop: "memberRank", label: "排名", type: "number" },
    { prop: "winStars", label: "胜利之星", type: "number" },
    { prop: "destroyRate", label: "摧毁率(%)", type: "number" },
    {
      prop: "actualAttacks",
      label: "实际攻击次数",
      type: "number",
      hideInTable: true,
      rule: req("请输入实际攻击次数"),
    },
    {
      prop: "requiredAttacks",
      label: "应该攻击次数",
      type: "number",
      hideInTable: true,
      rule: req("请输入应该攻击次数"),
    },
    {
      prop: "attackCount",
      label: "进攻次数",
      hideInForm: true,
      formatter: (row) =>
        (row.actualAttacks || 0) + "/" + (row.requiredAttacks || 0),
    },
    {
      prop: "hasExtra",
      label: "是否有额外",
      type: "select",
      default: 0,
      options: [
        { label: "否", value: 0 },
        { label: "是", value: 1 },
      ],
    },
    {
      prop: "signupStatus",
      label: "报名状态",
      type: "select",
      dictCode: "signup_status",
      search: true,
      default: "1",
    },
  ];
  const leagueSignupCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    // 联赛：名称为主 + 编号为辅（两行）
    {
      prop: "leagueNo",
      label: "联赛",
      search: true,
      extraProp: "leagueName",
      extraPropFirst: true,
      type: "remote-select",
      rule: req("请选择联赛编号"),
      url: "/api/league",
      labelKey: "leagueName",
      valueKey: "leagueNo",
      placeholder: "请输入关键字筛选联赛",
    },
    // 部落：名称为主 + 编号为辅（两行）
    {
      prop: "clanNo",
      label: "部落",
      search: true,
      extraProp: "clanName",
      extraPropFirst: true,
      type: "remote-select",
      rule: req("请选择部落编号"),
      url: "/api/clan",
      labelKey: "clanName",
      valueKey: "clanNo",
      placeholder: "请输入关键字筛选部落",
    },
    // 成员：合并显示 memberName（主值）+ memberNo（副值）
    {
      prop: "memberName",
      label: "成员",
      search: true,
      searchAsText: true,
      extraProp: "memberNo",
      type: "remote-select",
      rule: req("请选择成员名称"),
      url: "/api/clan/member",
      labelKey: "memberName",
      valueKey: "memberName",
      placeholder: "请输入关键字筛选成员，查不到可直接手填",
      // 下拉查不到成员时允许手填成员名
      allowCreate: true,
      // 选中成员时把成员编号带出到 memberNo 字段
      extraFields: ["memberNo"],
      fillProps: { memberNo: "memberNo" },
    },
    {
      prop: "memberNo",
      label: "成员编号",
      hideInTable: true,
    },
    {
      prop: "signupStatus",
      label: "报名状态",
      type: "select",
      dictCode: "signup_status",
      search: true,
      default: "1",
    },
    { prop: "signupTime", label: "报名时间", type: "date", hideInForm: true },
  ];
  const groupCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "groupNo",
      label: "群组编号",
      search: true,
      rule: req("请输入群组编号"),
    },
    {
      prop: "groupName",
      label: "群组名称",
      search: true,
      rule: req("请输入群组名称"),
    },
    // 表格列：直接展示后端关联返回的 ownerName（只读，仅表格显示）
    { prop: "ownerName", label: "群主", hideInForm: true },
    // 表单字段：用远程下拉选择用户，提交时传 ownerId（仅表单显示）
    {
      prop: "ownerId",
      label: "群主",
      type: "remote-select",
      hideInTable: true,
      url: "/api/sys/user",
      labelKey: "username",
      valueKey: "id",
      placeholder: "请输入用户名搜索选择群主（可留空）",
    },
    { prop: "intro", label: "简介", type: "textarea" },
    {
      prop: "status",
      label: "状态",
      type: "switch",
      activeText: "启用",
      inactiveText: "禁用",
    },
  ];
  const menuCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "menuName",
      label: "菜单名称",
      search: true,
      rule: req("请输入菜单名称"),
    },
    {
      prop: "menuType",
      label: "类型",
      type: "select",
      default: 1,
      options: [
        { label: "目录", value: 0 },
        { label: "菜单", value: 1 },
        { label: "按钮", value: 2 },
      ],
    },
    {
      prop: "parentId",
      label: "父菜单",
      type: "tree-select",
      default: 0,
      url: "/api/sys/menu",
      placeholder: "不选则为顶级菜单",
    },
    { prop: "path", label: "路径" },
    { prop: "component", label: "组件" },
    { prop: "icon", label: "图标" },
    { prop: "permission", label: "权限标识" },
    { prop: "sort", label: "排序", type: "number" },
  ];
  const dictGroupCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "groupCode",
      label: "分组编码",
      search: true,
      rule: req("请输入分组编码"),
    },
    {
      prop: "groupName",
      label: "分组名称",
      search: true,
      rule: req("请输入分组名称"),
    },
    { prop: "remark", label: "备注", type: "textarea" },
    {
      prop: "status",
      label: "状态",
      type: "switch",
      activeText: "启用",
      inactiveText: "禁用",
    },
  ];
  const dictItemCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "groupCode",
      label: "所属分组",
      type: "select",
      rule: req("请选择分组"),
      options: () => COC.dictGroups || [],
    },
    {
      prop: "itemName",
      label: "字典名称",
      search: true,
      rule: req("请输入字典名称"),
    },
    {
      prop: "itemValue",
      label: "字典值",
      search: true,
      rule: req("请输入字典值"),
    },
    { prop: "sort", label: "排序", type: "number" },
    {
      prop: "status",
      label: "状态",
      type: "switch",
      activeText: "启用",
      inactiveText: "禁用",
    },
  ];
  const configCols = [
    { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
    {
      prop: "configName",
      label: "配置名",
      search: true,
      rule: req("请输入配置名"),
      placeholder: "如 attack_score",
    },
    {
      prop: "configValue",
      label: "配置值",
      search: true,
      rule: req("请输入配置值"),
      placeholder: "数值或文本",
    },
    { prop: "description", label: "描述", type: "textarea", search: true },
  ];

  // 暴露到全局，供 crud-instances.js 使用
  window.COC_COLS = {
    req,
    clanCols,
    memberCols,
    warCols,
    warRecordCols,
    leagueCols,
    leagueClanScoreCols,
    leagueRecordCols,
    leagueSignupCols,
    groupCols,
    menuCols,
    dictGroupCols,
    dictItemCols,
    configCols,
  };
})();
