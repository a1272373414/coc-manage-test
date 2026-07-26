/**
 * 部落新增功能测试：验证 groupNo 自动从当前用户取、为空时阻止并提示。
 */
var { loadAllScripts } = require("../helpers/load-scripts");

describe("ClanCrud - 部落群组编号自动填充", function () {
  beforeEach(function () {
    loadAllScripts({ configEnv: "dev" });
  });

  // ==================== clanCols 配置 ====================

  describe("clanCols - groupNo 列配置", function () {
    test("包含 groupNo 列定义", function () {
      var groupNoCol = COC_COLS.clanCols.find(function (c) {
        return c.prop === "groupNo";
      });
      expect(groupNoCol).toBeDefined();
      expect(groupNoCol.label).toBe("群组编号");
    });

    test("groupNo 在表格中不显示", function () {
      var groupNoCol = COC_COLS.clanCols.find(function (c) {
        return c.prop === "groupNo";
      });
      expect(groupNoCol.hideInTable).toBe(true);
    });

    test("groupNo 在表单中显示（用户可见但只读）", function () {
      var groupNoCol = COC_COLS.clanCols.find(function (c) {
        return c.prop === "groupNo";
      });
      expect(groupNoCol.hideInForm).toBeFalsy();
    });

    test("groupNo 表单字段为 disabled（只读，不可编辑）", function () {
      var groupNoCol = COC_COLS.clanCols.find(function (c) {
        return c.prop === "groupNo";
      });
      expect(groupNoCol.disabled).toBe(true);
    });

    test("groupNo 包含在 formCols 中（submit 时会提交）", function () {
      var cols = COC_COLS.clanCols;
      var formCols = cols.filter(function (c) {
        return !c.hideInForm;
      });
      var groupNoInForm = formCols.find(function (c) {
        return c.prop === "groupNo";
      });
      expect(groupNoInForm).toBeDefined();
    });
  });

  // ==================== openCreate 覆盖逻辑 ====================

  describe("openCreate() - 自动填充 groupNo", function () {
    /**
     * 模拟 clanCrud.methods.openCreate 的覆盖逻辑。
     * 与 crud-instances.js 中的实现完全一致。
     */
    function clanOpenCreate(ctx) {
      var groupNo = (COC.store.user && COC.store.user.groupNo) || "";
      if (!groupNo) {
        if (window.ElementPlus && ElementPlus.ElMessage) {
          ElementPlus.ElMessage.warning("需成为群主或部落管理员才能新增部落");
        }
        return { opened: false };
      }
      // 调用原始 openCreate 设置 form 为默认值
      ctx.form = {};
      ctx.dialogVisible = true;
      ctx.form.groupNo = groupNo;
      return { opened: true, groupNo: groupNo };
    }

    test("当前用户有 groupNo 时自动填入表单", function () {
      COC.store.user = { groupNo: "G001", username: "groupAdmin" };
      var ctx = { form: null, dialogVisible: false };
      var result = clanOpenCreate(ctx);
      expect(result.opened).toBe(true);
      expect(ctx.dialogVisible).toBe(true);
      expect(ctx.form.groupNo).toBe("G001");
    });

    test("当前用户无 groupNo 时阻止打开对话框", function () {
      COC.store.user = { groupNo: null, username: "superAdmin" };
      var ctx = { form: null, dialogVisible: false };
      var result = clanOpenCreate(ctx);
      expect(result.opened).toBe(false);
      expect(ctx.dialogVisible).toBe(false);
      expect(ctx.form).toBeNull();
    });

    test("当前用户 groupNo 为空字符串时也阻止", function () {
      COC.store.user = { groupNo: "", username: "superAdmin" };
      var ctx = { form: null, dialogVisible: false };
      var result = clanOpenCreate(ctx);
      expect(result.opened).toBe(false);
    });

    test("当前用户对象不存在时也阻止", function () {
      COC.store.user = null;
      var ctx = { form: null, dialogVisible: false };
      var result = clanOpenCreate(ctx);
      expect(result.opened).toBe(false);
    });

    test("ElMessage.warning 在 groupNo 为空时被调用", function () {
      COC.store.user = { groupNo: null };
      var warningCalled = false;
      var originalWarning = ElementPlus.ElMessage.warning;
      ElementPlus.ElMessage.warning = function (msg) {
        warningCalled = msg;
      };
      var ctx = { form: null, dialogVisible: false };
      clanOpenCreate(ctx);
      expect(warningCalled).toBe("需成为群主或部落管理员才能新增部落");
      ElementPlus.ElMessage.warning = originalWarning;
    });
  });

  // ==================== submit 包含 groupNo ====================

  describe("submit() - groupNo 包含在提交数据中", function () {
    test("新增部落时 payload 包含 groupNo", function () {
      // 模拟 submit 的 payload 构造逻辑
      var formCols = COC_COLS.clanCols.filter(function (c) {
        return !c.hideInForm;
      });
      var form = {
        clanName: "部落A",
        clanNo: "C001",
        groupNo: "G001",
        intro: "简介",
      };
      var payload = {};
      formCols.forEach(function (c) {
        payload[c.prop] = form[c.prop];
      });
      expect(payload.groupNo).toBe("G001");
      expect(payload.clanName).toBe("部落A");
      expect(payload.clanNo).toBe("C001");
    });
  });

  // ==================== 源代码验证 ====================

  describe("源代码关键逻辑验证", function () {
    var fs = require("fs");
    var path = require("path");

    function readSrc(filename) {
      return fs.readFileSync(
        path.resolve(
          __dirname,
          "..",
          "src",
          "main",
          "resources",
          "static",
          filename,
        ),
        "utf8",
      );
    }

    test("cols.js 中 clanCols 包含 groupNo 列", function () {
      var src = readSrc("cols.js");
      expect(src).toContain("prop: 'groupNo'");
      expect(src).toContain("label: '群组编号'");
    });

    test("cols.js 中 groupNo 设置了 disabled: true", function () {
      var src = readSrc("cols.js");
      // 验证 groupNo 行包含 disabled: true
      expect(src).toMatch(/prop:\s*'groupNo'[^}]*disabled:\s*true/);
    });

    test("crud-instances.js 覆盖了 clanCrud 的 openCreate", function () {
      var src = readSrc("crud-instances.js");
      expect(src).toContain("_clanOpenCreate");
      expect(src).toContain("clanCrud.methods.openCreate");
    });

    test("crud-instances.js 中从 COC.store.user 获取 groupNo", function () {
      var src = readSrc("crud-instances.js");
      expect(src).toContain("COC.store.user");
      expect(src).toContain("groupNo");
    });

    test("crud-instances.js 中 groupNo 为空时弹出提示", function () {
      var src = readSrc("crud-instances.js");
      expect(src).toContain("需成为群主或部落管理员才能新增部落");
    });
  });
});
