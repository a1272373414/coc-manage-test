/**
 * crud.js 测试：验证通用 CRUD 组件工厂方法。
 * 通过 createCrud() 创建组件定义，调用其 methods 测试纯逻辑函数。
 */
var { loadAllScripts } = require("../helpers/load-scripts");

describe("crud.js - CRUD 组件工厂", function () {
  var crudComp;

  beforeEach(function () {
    loadAllScripts({ configEnv: "dev" });

    // 定义测试用列配置
    var testCols = [
      { prop: "id", label: "ID", hideInForm: true, hideInTable: true },
      {
        prop: "name",
        label: "名称",
        search: true,
        rule: [{ required: true, message: "请输入名称", trigger: "blur" }],
      },
      {
        prop: "status",
        label: "状态",
        type: "switch",
        activeText: "启用",
        inactiveText: "禁用",
        default: 1,
      },
      { prop: "count", label: "数量", type: "number", default: 0 },
      {
        prop: "category",
        label: "分类",
        type: "select",
        options: [
          { label: "A", value: "a" },
          { label: "B", value: "b" },
        ],
      },
      { prop: "intro", label: "简介", type: "textarea" },
    ];

    // 创建 CRUD 组件定义
    crudComp = createCrud({
      name: "TestCrud",
      baseUrl: "/api/test",
      cols: testCols,
    });
  });

  // ==================== 组件结构 ====================

  describe("组件结构", function () {
    test("createCrud 返回有效的组件定义对象", function () {
      expect(crudComp).toBeDefined();
      expect(crudComp.name).toBe("TestCrud");
      expect(typeof crudComp.data).toBe("function");
      expect(crudComp.methods).toBeDefined();
    });

    test("data() 包含必要的状态字段", function () {
      var data = crudComp.data();
      expect(data.baseUrl).toBe("/api/test");
      expect(data.keyword).toBe("");
      expect(data.list).toEqual([]);
      expect(data.total).toBe(0);
      expect(data.page).toBe(1);
      expect(data.size).toBe(10);
      expect(data.loading).toBe(false);
      expect(data.dialogVisible).toBe(false);
      expect(data.form).toEqual({});
    });
  });

  // ==================== computed 属性 ====================

  describe("computed 属性", function () {
    test("searchCols - 过滤出可搜索列", function () {
      // 模拟 Vue computed 调用
      var ctx = {
        cols: crudComp.data().cols,
        searchCols: crudComp.computed.searchCols,
      };
      var searchCols =
        typeof ctx.searchCols === "function"
          ? ctx.searchCols.call(ctx)
          : ctx.searchCols;
      // name 有 search:true，intro 无
      expect(searchCols.length).toBe(1);
      expect(searchCols[0].prop).toBe("name");
    });

    test("tableCols - 过滤出表格显示列（排除 hideInTable）", function () {
      var ctx = {
        cols: crudComp.data().cols,
        tableCols: crudComp.computed.tableCols,
      };
      var tableCols =
        typeof ctx.tableCols === "function"
          ? ctx.tableCols.call(ctx)
          : ctx.tableCols;
      // id 有 hideInTable，其余无
      expect(tableCols.length).toBe(5);
      expect(
        tableCols.find(function (c) {
          return c.prop === "id";
        }),
      ).toBeUndefined();
    });

    test("formCols - 过滤出表单字段（排除 hideInForm）", function () {
      var ctx = {
        cols: crudComp.data().cols,
        formCols: crudComp.computed.formCols,
      };
      var formCols =
        typeof ctx.formCols === "function"
          ? ctx.formCols.call(ctx)
          : ctx.formCols;
      // id 有 hideInForm
      expect(formCols.length).toBe(5);
      expect(
        formCols.find(function (c) {
          return c.prop === "id";
        }),
      ).toBeUndefined();
    });
  });

  // ==================== defaultForm ====================

  describe("defaultForm()", function () {
    test("switch 类型默认值为 1", function () {
      var ctx = Object.assign(crudComp.data(), {
        cols: crudComp.data().cols,
        formCols: crudComp.data().cols.filter(function (c) {
          return !c.hideInForm;
        }),
      });
      var form = crudComp.methods.defaultForm.call(ctx);
      expect(form.status).toBe(1);
    });

    test("number 类型默认值为 0", function () {
      var ctx = Object.assign(crudComp.data(), {
        cols: crudComp.data().cols,
        formCols: crudComp.data().cols.filter(function (c) {
          return !c.hideInForm;
        }),
      });
      var form = crudComp.methods.defaultForm.call(ctx);
      expect(form.count).toBe(0);
    });

    test("普通字段默认值为空字符串", function () {
      var ctx = Object.assign(crudComp.data(), {
        cols: crudComp.data().cols,
        formCols: crudComp.data().cols.filter(function (c) {
          return !c.hideInForm;
        }),
      });
      var form = crudComp.methods.defaultForm.call(ctx);
      expect(form.name).toBe("");
      expect(form.intro).toBe("");
    });

    test("指定 default 时使用 default 值", function () {
      var ctx = Object.assign(crudComp.data(), {
        cols: crudComp.data().cols,
        formCols: crudComp.data().cols.filter(function (c) {
          return !c.hideInForm;
        }),
      });
      var form = crudComp.methods.defaultForm.call(ctx);
      // status 有 default:1, count 有 default:0
      expect(form.status).toBe(1);
      expect(form.count).toBe(0);
    });
  });

  // ==================== optionsFor ====================

  describe("optionsFor()", function () {
    test("select 类型返回 c.options", function () {
      var ctx = {
        cols: crudComp.data().cols,
        dictOptions: {},
        remoteOptions: {},
      };
      var catCol = ctx.cols.find(function (c) {
        return c.prop === "category";
      });
      var opts = crudComp.methods.optionsFor.call(ctx, catCol);
      expect(opts).toEqual([
        { label: "A", value: "a" },
        { label: "B", value: "b" },
      ]);
    });

    test("options 为函数时调用函数返回结果", function () {
      var ctx = {
        cols: crudComp.data().cols,
        dictOptions: {},
        remoteOptions: {},
      };
      var col = {
        prop: "test",
        options: function () {
          return [{ label: "X", value: "x" }];
        },
      };
      var opts = crudComp.methods.optionsFor.call(ctx, col);
      expect(opts).toEqual([{ label: "X", value: "x" }]);
    });

    test("无匹配类型时返回空数组", function () {
      var ctx = {
        cols: crudComp.data().cols,
        dictOptions: {},
        remoteOptions: {},
      };
      var col = { prop: "plain" };
      var opts = crudComp.methods.optionsFor.call(ctx, col);
      expect(opts).toEqual([]);
    });
  });

  // ==================== labelOf ====================

  describe("labelOf()", function () {
    test("select 类型根据 value 返回对应 label", function () {
      var ctx = {
        cols: crudComp.data().cols,
        dictOptions: {},
        remoteOptions: {},
        optionsFor: crudComp.methods.optionsFor,
      };
      var catCol = ctx.cols.find(function (c) {
        return c.prop === "category";
      });
      var label = crudComp.methods.labelOf.call(ctx, catCol, "a");
      expect(label).toBe("A");
    });

    test("找不到匹配值时返回原始值", function () {
      var ctx = {
        cols: crudComp.data().cols,
        dictOptions: {},
        remoteOptions: {},
        optionsFor: crudComp.methods.optionsFor,
      };
      var catCol = ctx.cols.find(function (c) {
        return c.prop === "category";
      });
      var label = crudComp.methods.labelOf.call(ctx, catCol, "unknown");
      expect(label).toBe("unknown");
    });

    test("空值返回空字符串", function () {
      var ctx = {
        cols: crudComp.data().cols,
        dictOptions: {},
        remoteOptions: {},
        optionsFor: crudComp.methods.optionsFor,
      };
      var catCol = ctx.cols.find(function (c) {
        return c.prop === "category";
      });
      var label = crudComp.methods.labelOf.call(ctx, catCol, "");
      expect(label).toBe("");
    });
  });

  // ==================== openCreate / openEdit ====================

  describe("openCreate()", function () {
    test('设置 dialogTitle 为"新增"并打开对话框', function () {
      var ctx = Object.assign(crudComp.data(), {
        cols: crudComp.data().cols,
        formCols: crudComp.data().cols.filter(function (c) {
          return !c.hideInForm;
        }),
        formColsFilter: crudComp.data().cols.filter(function (c) {
          return !c.hideInForm;
        }),
        remoteOptions: {},
        remoteSearch: function () {},
        defaultForm: crudComp.methods.defaultForm,
        openCreate: crudComp.methods.openCreate,
        $nextTick: function (cb) {
          if (cb) cb();
        },
        $refs: {},
      });
      // 绑定 formCols 计算属性
      ctx.formCols = crudComp.data().cols.filter(function (c) {
        return !c.hideInForm;
      });

      crudComp.methods.openCreate.call(ctx);

      expect(ctx.dialogTitle).toBe("新增");
      expect(ctx.dialogVisible).toBe(true);
      expect(ctx.form.status).toBe(1);
      expect(ctx.form.count).toBe(0);
      expect(ctx.form.name).toBe("");
    });
  });

  describe("openEdit()", function () {
    test('设置 dialogTitle 为"编辑"并填充表单', function () {
      var row = {
        id: 42,
        name: "测试",
        status: 0,
        count: 5,
        category: "b",
        intro: "备注",
      };
      var ctx = Object.assign(crudComp.data(), {
        cols: crudComp.data().cols,
        formCols: crudComp.data().cols.filter(function (c) {
          return !c.hideInForm;
        }),
        remoteOptions: {},
        remoteSearch: function () {},
        defaultForm: crudComp.methods.defaultForm,
        $nextTick: function (cb) {
          if (cb) cb();
        },
        $refs: {},
      });

      crudComp.methods.openEdit.call(ctx, row);

      expect(ctx.dialogTitle).toBe("编辑");
      expect(ctx.dialogVisible).toBe(true);
      expect(ctx.form.id).toBe(42);
      expect(ctx.form.name).toBe("测试");
      expect(ctx.form.status).toBe(0);
      expect(ctx.form.count).toBe(5);
      expect(ctx.form.category).toBe("b");
    });

    test("switch 类型 null 值转为 1", function () {
      var row = {
        id: 1,
        name: "test",
        status: null,
        count: 0,
        category: "",
        intro: "",
      };
      var ctx = Object.assign(crudComp.data(), {
        cols: crudComp.data().cols,
        formCols: crudComp.data().cols.filter(function (c) {
          return !c.hideInForm;
        }),
        remoteOptions: {},
        remoteSearch: function () {},
        defaultForm: crudComp.methods.defaultForm,
        $nextTick: function (cb) {
          if (cb) cb();
        },
        $refs: {},
      });

      crudComp.methods.openEdit.call(ctx, row);
      expect(ctx.form.status).toBe(1);
    });
  });

  // ==================== onSearch / onReset ====================

  describe("onSearch() / onReset()", function () {
    test("onSearch 重置页码为 1", function () {
      var loaded = false;
      var ctx = {
        page: 3,
        load: function () {
          loaded = true;
        },
        onSearch: crudComp.methods.onSearch,
      };
      crudComp.methods.onSearch.call(ctx);
      expect(ctx.page).toBe(1);
      expect(loaded).toBe(true);
    });

    test("onReset 清空搜索条件并重置页码", function () {
      var loaded = false;
      var ctx = {
        keyword: "test",
        filters: { name: "abc", status: 1 },
        page: 5,
        load: function () {
          loaded = true;
        },
        onReset: crudComp.methods.onReset,
      };
      crudComp.methods.onReset.call(ctx);
      expect(ctx.keyword).toBe("");
      expect(ctx.filters).toEqual({});
      expect(ctx.page).toBe(1);
      expect(loaded).toBe(true);
    });
  });
});
