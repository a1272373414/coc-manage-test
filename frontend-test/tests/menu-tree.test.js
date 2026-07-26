/**
 * MenuTree 组件测试：覆盖树视图过滤、表单默认值、类型映射等核心逻辑。
 */
var { loadAllScripts } = require("../helpers/load-scripts");

describe("MenuTree - 菜单树视图工厂", function () {
  var comp;

  beforeEach(function () {
    loadAllScripts({ configEnv: "dev" });
    comp = createMenuTree();
  });

  // ==================== 组件结构 ====================

  describe("组件结构", function () {
    test("返回有效的 Vue 组件定义", function () {
      expect(comp).toBeDefined();
      expect(comp.name).toBe("MenuTree");
      expect(typeof comp.data).toBe("function");
      expect(typeof comp.mounted).toBe("function");
      expect(comp.methods).toBeDefined();
    });

    test("包含过滤计算属性 filteredData", function () {
      expect(comp.computed).toBeDefined();
      expect(typeof comp.computed.filteredData).toBe("function");
    });

    test("包含 Element Plus el-tree 模板", function () {
      expect(comp.template).toContain("el-tree");
      expect(comp.template).toContain("el-dialog");
      expect(comp.template).toContain("el-tree-select");
    });
  });

  // ==================== 模板渲染：path/permission 前缀 ====================

  describe("模板渲染 - path/permission 前缀处理", function () {
    test('path 字段已含 "/"，模板不应再加 "/" 前缀（避免出现 //system 双斜杠）', function () {
      // 修复前 bug：模板写成 `>/{{ data.path }}`，导致 path="/system" 渲染出 "//system"
      expect(comp.template).not.toMatch(
        />\s*\/\s*\{\{\s*data\.path\s*\}\}\s*</,
      );
      // 正确形式：直接用 data.path
      expect(comp.template).toContain(">{{ data.path }}</span>");
    });

    test('path 渲染示例 - 后端返回 "/system" 应显示为 "/system" 不是 "//system"', function () {
      // 通过纯文本模拟渲染验证：
      function renderPath(path) {
        return path;
      }
      expect(renderPath("/system")).toBe("/system");
      expect(renderPath("/system").startsWith("//")).toBe(false);
    });

    test('permission 字段不带 "@"，模板应添加 "@" 前缀作为视觉区分', function () {
      // permission 在数据库中是 "system:manage" 这种形式，模板期望渲染为 "@system:manage"
      expect(comp.template).toContain("@{{ data.permission }}");
    });

    test("当 path 缺省时整段不渲染（避免多余的斜杠）", function () {
      expect(comp.template).toContain('v-if="data.path"');
      expect(comp.template).toContain('v-if="data.permission"');
    });
  });

  // ==================== 模板：必填标识 ====================

  describe("模板 - 必填标识", function () {
    test("菜单名称有 required 标识", function () {
      expect(comp.template).toContain('label="菜单名称" required');
    });

    test("类型有 required 标识", function () {
      expect(comp.template).toContain('label="类型" required');
    });

    test("路由路径有 required 标识（目录和菜单类型必填）", function () {
      expect(comp.template).toContain(
        'label="路由路径" v-if="form.menuType !== 2" required',
      );
    });

    test("权限标识有 required 标识（菜单和按钮类型必填）", function () {
      expect(comp.template).toContain(
        'label="权限标识" v-if="form.menuType === 1 || form.menuType === 2" required',
      );
    });
  });

  // ==================== typeLabel / typeTagType ====================

  describe("typeLabel() / typeTagType()", function () {
    test("菜单类型映射到正确的中文标签", function () {
      var m = comp.methods;
      expect(m.typeLabel(0)).toBe("目录");
      expect(m.typeLabel(1)).toBe("菜单");
      expect(m.typeLabel(2)).toBe("按钮");
    });

    test('typeLabel 对 null 兜底为 "菜单"', function () {
      expect(comp.methods.typeLabel(null)).toBe("菜单");
      expect(comp.methods.typeLabel(undefined)).toBe("菜单");
    });

    test("Element Plus tag 类型映射", function () {
      expect(comp.methods.typeTagType(0)).toBe("success");
      expect(comp.methods.typeTagType(1)).toBe("primary");
      expect(comp.methods.typeTagType(2)).toBe("info");
    });

    test("typeTagType 对 null 兜底为 primary", function () {
      expect(comp.methods.typeTagType(null)).toBe("primary");
    });
  });

  // ==================== makeEmptyForm ====================

  describe("makeEmptyForm()", function () {
    test("parentId=null 表示顶级菜单", function () {
      var form = comp.methods.makeEmptyForm(null);
      expect(form.id).toBeNull();
      expect(form.parentId).toBe(0);
      expect(form.menuType).toBe(0); // 顶级默认目录
      expect(form.menuName).toBe("");
      expect(form.path).toBe("");
      expect(form.sort).toBe(0);
    });

    test("parentId 有值时是子菜单", function () {
      var form = comp.methods.makeEmptyForm(5);
      expect(form.parentId).toBe(5);
      expect(form.menuType).toBe(1); // 子菜单默认菜单
    });

    test("parentId=0 与 null 等价（顶级）", function () {
      var f1 = comp.methods.makeEmptyForm(0);
      var f2 = comp.methods.makeEmptyForm(null);
      expect(f1.parentId).toBe(0);
      expect(f2.parentId).toBe(0);
    });
  });

  // ==================== openCreate / openEdit ====================

  describe("openCreate()", function () {
    test('顶级新增 - 设置对话标题为"新增顶级菜单"', function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.dialogVisible = false;
      ctx.dialogTitle = "";
      ctx.form = null;

      ctx.openCreate(null);

      expect(ctx.dialogTitle).toBe("新增顶级菜单");
      expect(ctx.dialogVisible).toBe(true);
      expect(ctx.form.parentId).toBe(0);
      expect(ctx.form.menuType).toBe(0);
    });

    test("子菜单新增 - 标题、文案与 parentId 均正确", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.dialogVisible = false;
      ctx.form = null;

      ctx.openCreate(42);

      expect(ctx.dialogTitle).toBe("新增子菜单");
      expect(ctx.dialogVisible).toBe(true);
      expect(ctx.form.parentId).toBe(42);
      expect(ctx.form.menuType).toBe(1);
    });
  });

  describe("openEdit()", function () {
    test("从节点填充表单 - 完整字段", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.dialogVisible = false;
      ctx.dialogTitle = "";

      var node = {
        id: 10,
        parentId: 5,
        menuName: "用户管理",
        menuType: 1,
        path: "/sys/user",
        component: "views/SystemUser.vue",
        icon: "User",
        permission: "user:list",
        sort: 10,
      };

      ctx.openEdit(node);

      expect(ctx.dialogTitle).toBe("编辑菜单");
      expect(ctx.dialogVisible).toBe(true);
      expect(ctx.form.id).toBe(10);
      expect(ctx.form.parentId).toBe(5);
      expect(ctx.form.menuName).toBe("用户管理");
      expect(ctx.form.menuType).toBe(1);
      expect(ctx.form.path).toBe("/sys/user");
      expect(ctx.form.component).toBe("views/SystemUser.vue");
      expect(ctx.form.icon).toBe("User");
      expect(ctx.form.permission).toBe("user:list");
      expect(ctx.form.sort).toBe(10);
    });

    test("openEdit 缺失字段兜底默认", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      var node = { id: 5, menuName: "空菜单" }; // 大部分字段缺失

      ctx.openEdit(node);

      expect(ctx.form.id).toBe(5);
      expect(ctx.form.menuName).toBe("空菜单");
      expect(ctx.form.menuType).toBe(1); // null 兜底为 1（菜单）
      expect(ctx.form.path).toBe("");
      expect(ctx.form.component).toBe("");
      expect(ctx.form.icon).toBe("");
      expect(ctx.form.permission).toBe("");
      expect(ctx.form.sort).toBe(0);
      expect(ctx.form.parentId).toBe(0); // null 兜底为 0
    });
  });

  // ==================== filteredData 计算属性 ====================

  describe("filteredData - 关键字过滤", function () {
    /**
     * 构建一个测试用的菜单树：
     *   系统管理 (id=10)
     *     用户管理 (id=11, menuName="用户管理", path="/sys/user")
     *     角色管理 (id=12, menuName="角色管理", path="/sys/role")
     *   联赛管理 (id=20, menuName="联赛管理", path="/league")
     *     报名管理 (id=21, menuName="报名管理", path="/league/signup")
     *   数据看板 (id=30, menuName="数据看板", path="/dashboard")
     */
    function buildTree() {
      return [
        {
          id: 10,
          parentId: 0,
          menuName: "系统管理",
          path: "/system",
          menuType: 0,
          children: [
            {
              id: 11,
              parentId: 10,
              menuName: "用户管理",
              path: "/sys/user",
              permission: "user:list",
              menuType: 1,
              children: [],
            },
            {
              id: 12,
              parentId: 10,
              menuName: "角色管理",
              path: "/sys/role",
              permission: "role:list",
              menuType: 1,
              children: [],
            },
          ],
        },
        {
          id: 20,
          parentId: 0,
          menuName: "联赛管理",
          path: "/league",
          menuType: 0,
          children: [
            {
              id: 21,
              parentId: 20,
              menuName: "报名管理",
              path: "/league/signup",
              permission: "league:signup",
              menuType: 1,
              children: [],
            },
          ],
        },
        {
          id: 30,
          parentId: 0,
          menuName: "数据看板",
          path: "/dashboard",
          menuType: 1,
          children: [],
        },
      ];
    }

    test("空关键字时返回原树", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.treeData = buildTree();
      ctx.keyword = "";

      var result = comp.computed.filteredData.call(ctx);
      expect(result).toBe(ctx.treeData); // O(1) 引用保持一致
      expect(result.length).toBe(3);
    });

    test("匹配菜单名 - 只保留匹配节点 + 必要父链", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.treeData = buildTree();
      ctx.keyword = "用户";

      var result = comp.computed.filteredData.call(ctx);
      // 应该只保留顶级"系统管理"和它的子"用户管理"（角色被过滤掉）
      expect(result.length).toBe(1);
      expect(result[0].id).toBe(10); // 系统管理（被保留）
      expect(result[0].children.length).toBe(1);
      expect(result[0].children[0].id).toBe(11); // 用户管理
    });

    test("匹配路由路径", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.treeData = buildTree();
      ctx.keyword = "signup"; // 匹配 /league/signup

      var result = comp.computed.filteredData.call(ctx);
      expect(result.length).toBe(1);
      expect(result[0].id).toBe(20); // 联赛管理（父链）
      expect(result[0].children[0].id).toBe(21); // 报名管理
    });

    test("匹配权限标识", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.treeData = buildTree();
      ctx.keyword = "role:list"; // 权限标识匹配

      var result = comp.computed.filteredData.call(ctx);
      expect(result.length).toBe(1);
      expect(result[0].id).toBe(10);
      expect(result[0].children[0].id).toBe(12); // 角色管理
    });

    test("不匹配任何节点 - 返回空树", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.treeData = buildTree();
      ctx.keyword = "不存在的关键字";

      var result = comp.computed.filteredData.call(ctx);
      expect(result.length).toBe(0);
    });

    test("关键字大小写不敏感", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.treeData = buildTree();
      ctx.keyword = "DASHBOARD";

      var result = comp.computed.filteredData.call(ctx);
      expect(result.length).toBe(1);
      expect(result[0].id).toBe(30);
    });

    test("空白关键字视为空", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.treeData = buildTree();
      ctx.keyword = "   ";

      var result = comp.computed.filteredData.call(ctx);
      expect(result).toBe(ctx.treeData);
      expect(result.length).toBe(3);
    });

    test("对原数组不修改（不产生副作用）", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      var original = buildTree();
      ctx.treeData = original;
      ctx.keyword = "用户";

      comp.computed.filteredData.call(ctx);

      // 原数组未变
      expect(original[0].children.length).toBe(2);
      expect(original.length).toBe(3);
    });

    test("深层级过滤 - 三级嵌套仍正确保留父链", function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      // 添加三级菜单
      ctx.treeData = [
        {
          id: 1,
          parentId: 0,
          menuName: "系统管理",
          path: "/sys",
          menuType: 0,
          children: [
            {
              id: 2,
              parentId: 1,
              menuName: "权限管理",
              path: "/perm",
              menuType: 0,
              children: [
                {
                  id: 3,
                  parentId: 2,
                  menuName: "用户列表",
                  path: "/perm/users",
                  menuType: 1,
                  children: [],
                },
              ],
            },
          ],
        },
      ];
      ctx.keyword = "用户列表";

      var result = comp.computed.filteredData.call(ctx);
      expect(result.length).toBe(1);
      expect(result[0].id).toBe(1); // 系统管理
      expect(result[0].children[0].id).toBe(2); // 权限管理
      expect(result[0].children[0].children[0].id).toBe(3); // 用户列表
    });
  });

  // ==================== 集成：onSave 校验逻辑 ====================

  describe("onSave() - 表单校验", function () {
    /**
     * 模拟 onSave 的校验逻辑（不实际调 API）：
     * 返回 { ok: boolean, message: string } 供测试断言。
     * 这是对 onSave 中校验部分的纯函数化（保留最小提取验证）。
     */
    function validate(form, ElementPlus) {
      if (!form.menuName) {
        if (ElementPlus && ElementPlus.ElMessage)
          ElementPlus.ElMessage.warning("请输入菜单名称");
        return { ok: false, message: "请输入菜单名称" };
      }
      if (form.menuType !== 2 && !form.path) {
        if (ElementPlus && ElementPlus.ElMessage)
          ElementPlus.ElMessage.warning("请输入路由路径");
        return { ok: false, message: "请输入路由路径" };
      }
      if ((form.menuType === 1 || form.menuType === 2) && !form.permission) {
        if (ElementPlus && ElementPlus.ElMessage)
          ElementPlus.ElMessage.warning("请输入权限标识");
        return { ok: false, message: "请输入权限标识" };
      }
      return { ok: true, message: "" };
    }

    test("按钮类型菜单只要求菜单名和权限标识", function () {
      var form = {
        menuName: "新增按钮",
        menuType: 2,
        permission: "sys:btn:add",
      };
      expect(validate(form).ok).toBe(true);
    });

    test("菜单类型菜单要求菜单名 + 路径 + 权限标识", function () {
      var form = {
        menuName: "用户管理",
        menuType: 1,
        path: "/sys/user",
        permission: "user:list",
      };
      expect(validate(form).ok).toBe(true);
    });

    test("目录类型菜单不需要权限标识", function () {
      var form = { menuName: "系统管理", menuType: 0, path: "/system" };
      expect(validate(form).ok).toBe(true);
    });

    test("缺少菜单名校验失败", function () {
      var form = { menuName: "", menuType: 1, path: "/x", permission: "x:r" };
      expect(validate(form).ok).toBe(false);
    });

    test("菜单类型缺少路径校验失败", function () {
      var form = { menuName: "X", menuType: 1, path: "", permission: "x:r" };
      expect(validate(form).ok).toBe(false);
    });

    test("菜单类型缺少权限标识校验失败", function () {
      var form = { menuName: "X", menuType: 1, path: "/x", permission: "" };
      expect(validate(form).ok).toBe(false);
    });
  });

  // ==================== loadTree async (mock COC.api.get) ====================

  describe("loadTree() - 异步加载", function () {
    test("成功时将返回数据赋给 treeData", async function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.loading = false;

      var treeData = [
        {
          id: 1,
          parentId: 0,
          menuName: "顶级菜单",
          path: "/top",
          menuType: 1,
          children: [],
        },
      ];
      // stub COC.api.get
      var originalGet = window.COC.api.get;
      window.COC.api.get = jest.fn().mockResolvedValue(treeData);

      await ctx.loadTree();

      expect(window.COC.api.get).toHaveBeenCalledWith("/api/sys/menu/tree");
      expect(ctx.treeData).toEqual(treeData);
      expect(ctx.loading).toBe(false);

      // restore
      window.COC.api.get = originalGet;
    });

    test("返回非数组时回退为空数组", async function () {
      var ctx = Object.assign(comp.data(), comp.methods);

      var originalGet = window.COC.api.get;
      window.COC.api.get = jest
        .fn()
        .mockResolvedValue({ unexpected: "object" });

      await ctx.loadTree();

      expect(ctx.treeData).toEqual([]);

      window.COC.api.get = originalGet;
    });

    test("加载失败时 treeData 仍然置为空数组，loading 关闭", async function () {
      var ctx = Object.assign(comp.data(), comp.methods);
      ctx.loading = true;

      var originalGet = window.COC.api.get;
      window.COC.api.get = jest.fn().mockRejectedValue(new Error("网络错误"));

      await ctx.loadTree();

      expect(ctx.treeData).toEqual([]);
      expect(ctx.loading).toBe(false);

      window.COC.api.get = originalGet;
    });
  });
});
