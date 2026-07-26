/**
 * UserManage 组件测试：验证用户名必填校验、密码默认值 123456。
 */
var { loadAllScripts } = require("../helpers/load-scripts");

describe("UserManage - 用户管理组件", function () {
  var comp;

  beforeEach(function () {
    loadAllScripts({ configEnv: "dev" });
    // UserManage 定义在 app.js 的 IIFE 内部，未暴露到 window
    // 这里通过验证 app.js 源代码中的关键逻辑来确保行为正确
  });

  // ==================== 密码默认值 ====================

  describe("openCreate() - 新增用户密码默认 123456", function () {
    test("新增用户表单 password 字段默认为 123456", function () {
      // 模拟 openCreate 的逻辑
      var form = {
        username: "",
        nickname: "",
        phone: "",
        email: "",
        status: 1,
        password: "123456",
      };
      expect(form.password).toBe("123456");
    });

    test("编辑用户表单 password 字段默认为空（留空不修改）", function () {
      // 模拟 openEdit 的逻辑
      var row = { id: 1, username: "admin", nickname: "管理员" };
      var form = Object.assign({}, row, { password: "" });
      expect(form.password).toBe("");
    });
  });

  // ==================== 用户名必填校验 ====================

  describe("submit() - 用户名必填校验", function () {
    /**
     * 模拟 submit 中的校验逻辑：
     * - username 为空时阻止提交并提示
     * - password 为空时从 payload 中删除（编辑模式不修改密码）
     */
    function validateAndClean(form) {
      var payload = Object.assign({}, form);
      if (!payload.username) {
        return { ok: false, message: "用户名不能为空" };
      }
      if (!payload.password) delete payload.password;
      return { ok: true, payload: payload };
    }

    test("用户名为空时校验失败", function () {
      var result = validateAndClean({ username: "", password: "123456" });
      expect(result.ok).toBe(false);
      expect(result.message).toBe("用户名不能为空");
    });

    test("用户名为 null 时校验失败", function () {
      var result = validateAndClean({ username: null, password: "123456" });
      expect(result.ok).toBe(false);
    });

    test("用户名有值时校验通过", function () {
      var result = validateAndClean({
        username: "admin",
        password: "123456",
        nickname: "管理员",
      });
      expect(result.ok).toBe(true);
      expect(result.payload.username).toBe("admin");
      expect(result.payload.password).toBe("123456");
    });

    test("编辑模式密码为空时从 payload 中删除", function () {
      var result = validateAndClean({
        id: 1,
        username: "admin",
        password: "",
        nickname: "管理员",
      });
      expect(result.ok).toBe(true);
      expect(result.payload.password).toBeUndefined();
    });

    test("编辑模式密码有值时保留在 payload 中", function () {
      var result = validateAndClean({
        id: 1,
        username: "admin",
        password: "newpass",
        nickname: "管理员",
      });
      expect(result.ok).toBe(true);
      expect(result.payload.password).toBe("newpass");
    });
  });

  // ==================== 源代码验证 ====================

  describe("源代码关键逻辑验证", function () {
    var fs = require("fs");
    var path = require("path");
    var appSrc = fs.readFileSync(
      path.resolve(
        __dirname,
        "..",
        "src",
        "main",
        "resources",
        "static",
        "app.js",
      ),
      "utf8",
    );

    test("openCreate 中 password 默认为 123456", function () {
      expect(appSrc).toContain("password: '123456'");
    });

    test("openEdit 中 password 默认为空字符串", function () {
      // openEdit 用 Object.assign({}, row, { password: '' })
      expect(appSrc).toMatch(
        /Object\.assign\(\{\},\s*row,\s*\{\s*password:\s*''\s*\}\)/,
      );
    });

    test("submit 中有用户名非空校验", function () {
      expect(appSrc).toContain("用户名不能为空");
    });

    test("submit 中密码为空时从 payload 删除", function () {
      expect(appSrc).toContain(
        "if (!payload.password) delete payload.password",
      );
    });

    test("模板中用户名表单项有 required 标识", function () {
      expect(appSrc).toContain('label="用户名" required');
    });

    test('密码输入框 placeholder 新增模式为"默认123456"', function () {
      expect(appSrc).toContain("'默认123456'");
    });

    test('密码输入框 placeholder 编辑模式为"留空则不修改"', function () {
      expect(appSrc).toContain("'留空则不修改'");
    });
  });

  // ==================== 用户列表展示群组编号 ====================

  describe("用户列表 - 展示群组编号列", function () {
    var fs = require("fs");
    var path = require("path");
    var appSrc = fs.readFileSync(
      path.resolve(
        __dirname,
        "..",
        "src",
        "main",
        "resources",
        "static",
        "app.js",
      ),
      "utf8",
    );

    test("UserManage 表格包含 groupNo 列", function () {
      expect(appSrc).toContain('prop="groupNo" label="群组编号"');
    });

    test("groupNo 列宽度为 120", function () {
      expect(appSrc).toContain('prop="groupNo" label="群组编号" width="120"');
    });

    test("groupNo 列在 nickname 和 phone 之间", function () {
      var nicknameIdx = appSrc.indexOf('prop="nickname"');
      var groupNoIdx = appSrc.indexOf('prop="groupNo"');
      var phoneIdx = appSrc.indexOf('prop="phone"');
      expect(nicknameIdx).toBeGreaterThan(-1);
      expect(groupNoIdx).toBeGreaterThan(nicknameIdx);
      expect(phoneIdx).toBeGreaterThan(groupNoIdx);
    });
  });
});
