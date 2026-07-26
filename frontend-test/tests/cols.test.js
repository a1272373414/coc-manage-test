/**
 * cols.js 测试：验证列配置完整性和 req 校验函数。
 */
var { loadAllScripts } = require("../helpers/load-scripts");

describe("cols.js - 列配置", function () {
  beforeEach(function () {
    loadAllScripts({ configEnv: "dev" });
  });

  // ==================== req 函数 ====================

  describe("req() - 必填校验规则", function () {
    test("生成 required:true 的校验规则", function () {
      var rule = COC_COLS.req("请输入名称");
      expect(Array.isArray(rule)).toBe(true);
      expect(rule.length).toBe(1);
      expect(rule[0].required).toBe(true);
      expect(rule[0].message).toBe("请输入名称");
      expect(rule[0].trigger).toBe("blur");
    });

    test("不同消息生成不同规则", function () {
      var r1 = COC_COLS.req("请输入名称");
      var r2 = COC_COLS.req("请输入编号");
      expect(r1[0].message).toBe("请输入名称");
      expect(r2[0].message).toBe("请输入编号");
    });
  });

  // ==================== 列配置完整性 ====================

  describe("列配置完整性", function () {
    test("所有列配置都暴露在 COC_COLS 上", function () {
      expect(COC_COLS.clanCols).toBeDefined();
      expect(COC_COLS.memberCols).toBeDefined();
      expect(COC_COLS.warCols).toBeDefined();
      expect(COC_COLS.warRecordCols).toBeDefined();
      expect(COC_COLS.leagueCols).toBeDefined();
      expect(COC_COLS.leagueRecordCols).toBeDefined();
      expect(COC_COLS.leagueSignupCols).toBeDefined();
      expect(COC_COLS.groupCols).toBeDefined();
      expect(COC_COLS.menuCols).toBeDefined();
      expect(COC_COLS.dictGroupCols).toBeDefined();
      expect(COC_COLS.dictItemCols).toBeDefined();
    });

    test("每列都有 prop 和 label", function () {
      var allCols = COC_COLS.clanCols
        .concat(COC_COLS.memberCols)
        .concat(COC_COLS.warCols)
        .concat(COC_COLS.warRecordCols)
        .concat(COC_COLS.leagueCols)
        .concat(COC_COLS.leagueRecordCols)
        .concat(COC_COLS.leagueSignupCols)
        .concat(COC_COLS.groupCols)
        .concat(COC_COLS.menuCols)
        .concat(COC_COLS.dictGroupCols)
        .concat(COC_COLS.dictItemCols);

      allCols.forEach(function (col) {
        expect(col.prop).toBeDefined();
        expect(typeof col.prop).toBe("string");
        expect(col.prop.length).toBeGreaterThan(0);
        expect(col.label).toBeDefined();
        expect(typeof col.label).toBe("string");
        expect(col.label.length).toBeGreaterThan(0);
      });
    });

    test("ID 列统一配置 hideInForm 和 hideInTable", function () {
      var groups = [
        COC_COLS.clanCols,
        COC_COLS.memberCols,
        COC_COLS.warCols,
        COC_COLS.warRecordCols,
        COC_COLS.leagueCols,
        COC_COLS.leagueRecordCols,
        COC_COLS.leagueSignupCols,
        COC_COLS.groupCols,
        COC_COLS.menuCols,
        COC_COLS.dictGroupCols,
        COC_COLS.dictItemCols,
      ];
      groups.forEach(function (cols) {
        var idCol = cols.find(function (c) {
          return c.prop === "id";
        });
        if (idCol) {
          expect(idCol.hideInForm).toBe(true);
          expect(idCol.hideInTable).toBe(true);
        }
      });
    });
  });

  // ==================== 部落成员列配置 ====================

  describe("memberCols - 部落成员列", function () {
    test("成员编号为非必填（无 rule）", function () {
      var col = COC_COLS.memberCols.find(function (c) {
        return c.prop === "memberNo";
      });
      expect(col.rule).toBeUndefined();
    });

    test("包含在组状态字段（select 类型，已加入/已退出）", function () {
      var col = COC_COLS.memberCols.find(function (c) {
        return c.prop === "memberStatus";
      });
      expect(col).toBeDefined();
      expect(col.type).toBe("select");
      expect(col.options).toEqual([
        { label: "已加入", value: 1 },
        { label: "已退出", value: 0 },
      ]);
      expect(col.default).toBe(1);
    });

    test("在组状态为必填字段（有 rule）", function () {
      var col = COC_COLS.memberCols.find(function (c) {
        return c.prop === "memberStatus";
      });
      expect(col.rule).toBeDefined();
      expect(col.rule[0].required).toBe(true);
    });

    test("在组状态为搜索筛选字段", function () {
      var col = COC_COLS.memberCols.find(function (c) {
        return c.prop === "memberStatus";
      });
      expect(col.search).toBe(true);
    });

    test("参战状态为 switch 类型且无自定义文字（默认参战状态）", function () {
      var col = COC_COLS.memberCols.find(function (c) {
        return c.prop === "warStatus";
      });
      expect(col.type).toBe("switch");
      expect(col.label).toBe("默认参战状态");
      expect(col.activeText).toBeUndefined();
      expect(col.inactiveText).toBeUndefined();
      expect(col.default).toBe(1);
    });

    test("参战状态为搜索筛选字段", function () {
      var col = COC_COLS.memberCols.find(function (c) {
        return c.prop === "warStatus";
      });
      expect(col.search).toBe(true);
    });

    test("部落编号为远程下拉选择（remote-select）", function () {
      var col = COC_COLS.memberCols.find(function (c) {
        return c.prop === "clanNo";
      });
      expect(col.type).toBe("remote-select");
      expect(col.url).toBe("/api/clan");
      expect(col.labelKey).toBe("clanName");
      expect(col.valueKey).toBe("clanNo");
      expect(col.placeholder).toBe("请输入关键字筛选部落");
    });

    test('部落编号 label 为"所属部落"且为必填', function () {
      var col = COC_COLS.memberCols.find(function (c) {
        return c.prop === "clanNo";
      });
      expect(col.label).toBe("所属部落");
      expect(col.rule).toBeDefined();
      expect(col.rule[0].required).toBe(true);
    });

    test("部落编号保留 search 属性（表格列可搜索）", function () {
      var col = COC_COLS.memberCols.find(function (c) {
        return c.prop === "clanNo";
      });
      expect(col.search).toBe(true);
    });
  });

  // ==================== 部落列配置 ====================

  describe("clanCols - 部落列", function () {
    test("包含部落名称和编号搜索字段", function () {
      var nameCol = COC_COLS.clanCols.find(function (c) {
        return c.prop === "clanName";
      });
      expect(nameCol.search).toBe(true);
      expect(nameCol.rule).toBeDefined();

      var noCol = COC_COLS.clanCols.find(function (c) {
        return c.prop === "clanNo";
      });
      expect(noCol.search).toBe(true);
      expect(noCol.rule).toBeDefined();
    });

    test("简介为 textarea 类型", function () {
      var introCol = COC_COLS.clanCols.find(function (c) {
        return c.prop === "intro";
      });
      expect(introCol.type).toBe("textarea");
    });
  });

  // ==================== 联赛列配置 ====================

  describe("leagueCols - 联赛列", function () {
    test("升降级为 select 类型并有选项", function () {
      var col = COC_COLS.leagueCols.find(function (c) {
        return c.prop === "promoteStatus";
      });
      expect(col.type).toBe("select");
      expect(col.options).toEqual([
        { label: "无", value: 0 },
        { label: "晋升", value: 1 },
        { label: "降级", value: 2 },
      ]);
      expect(col.default).toBe(0);
    });

    test("联赛段位为字典下拉（dictCode=league_tier）", function () {
      var col = COC_COLS.leagueCols.find(function (c) {
        return c.prop === "tier";
      });
      expect(col.label).toBe("联赛段位");
      expect(col.type).toBe("select");
      expect(col.dictCode).toBe("league_tier");
    });

    test("联赛段位为搜索筛选字段", function () {
      var col = COC_COLS.leagueCols.find(function (c) {
        return c.prop === "tier";
      });
      expect(col.search).toBe(true);
    });

    test("报名开始/结束为 date 类型", function () {
      var startCol = COC_COLS.leagueCols.find(function (c) {
        return c.prop === "signupStart";
      });
      expect(startCol.type).toBe("date");
      var endCol = COC_COLS.leagueCols.find(function (c) {
        return c.prop === "signupEnd";
      });
      expect(endCol.type).toBe("date");
    });
  });

  // ==================== 菜单列配置 ====================

  describe("menuCols - 菜单列", function () {
    test("父菜单为 tree-select 类型", function () {
      var col = COC_COLS.menuCols.find(function (c) {
        return c.prop === "parentId";
      });
      expect(col.type).toBe("tree-select");
      expect(col.url).toBe("/api/sys/menu");
      expect(col.default).toBe(0);
    });

    test("菜单类型为 select 并有目录/菜单/按钮选项", function () {
      var col = COC_COLS.menuCols.find(function (c) {
        return c.prop === "menuType";
      });
      expect(col.type).toBe("select");
      expect(col.options).toEqual([
        { label: "目录", value: 0 },
        { label: "菜单", value: 1 },
        { label: "按钮", value: 2 },
      ]);
      expect(col.default).toBe(1);
    });
  });

  // ==================== 字典列配置 ====================

  describe("dictItemCols - 字典项列", function () {
    test("所属分组为 select 类型且 options 为函数", function () {
      var col = COC_COLS.dictItemCols.find(function (c) {
        return c.prop === "groupCode";
      });
      expect(col.type).toBe("select");
      expect(typeof col.options).toBe("function");
      expect(col.rule).toBeDefined();
    });

    test("状态为 switch 类型", function () {
      var col = COC_COLS.dictItemCols.find(function (c) {
        return c.prop === "status";
      });
      expect(col.type).toBe("switch");
      expect(col.activeText).toBe("启用");
      expect(col.inactiveText).toBe("禁用");
    });
  });

  // ==================== 群组列配置 ====================

  describe("groupCols - 群组列", function () {
    test("群主为 remote-select 类型", function () {
      var col = COC_COLS.groupCols.find(function (c) {
        return c.prop === "ownerId";
      });
      expect(col.type).toBe("remote-select");
      expect(col.url).toBe("/api/sys/user");
      expect(col.labelKey).toBe("username");
      expect(col.valueKey).toBe("id");
      expect(col.hideInTable).toBe(true);
    });

    test("群主名称仅在表格显示（hideInForm）", function () {
      var col = COC_COLS.groupCols.find(function (c) {
        return c.prop === "ownerName";
      });
      expect(col.hideInForm).toBe(true);
    });
  });
});
