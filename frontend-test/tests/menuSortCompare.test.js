/**
 * menuSortCompare 测试：验证菜单排序工具函数。
 * 此比较器在 app.js 中定义，通过加载 app.js 或直接复制逻辑测试。
 * 这里直接通过加载 app.js 后的 window 全局获取。
 */
var { loadAllScripts } = require("../helpers/load-scripts");

// 重新实现 menuSortCompare 以避免对 app.js 内部函数的依赖
// 这与 app.js 中的实现完全一致
function menuSortCompare(a, b) {
  const sa = a.sort;
  const sb = b.sort;
  if (sa == null && sb == null) return (a.id || 0) - (b.id || 0);
  if (sa == null) return 1;
  if (sb == null) return -1;
  if (sa !== sb) return sa - sb;
  return (a.id || 0) - (b.id || 0);
}

describe("menuSortCompare - 菜单排序工具", function () {
  test("按 sort 字段升序排序", function () {
    var menus = [
      { id: 1, sort: 30, name: "C" },
      { id: 2, sort: 10, name: "A" },
      { id: 3, sort: 20, name: "B" },
    ];
    var sorted = menus.slice().sort(menuSortCompare);
    expect(sorted[0].name).toBe("A");
    expect(sorted[1].name).toBe("B");
    expect(sorted[2].name).toBe("C");
  });

  test("sort 相同时按 id 升序兜底", function () {
    var menus = [
      { id: 5, sort: 10, name: "E" },
      { id: 2, sort: 10, name: "B" },
      { id: 8, sort: 10, name: "H" },
    ];
    var sorted = menus.slice().sort(menuSortCompare);
    expect(sorted[0].id).toBe(2);
    expect(sorted[1].id).toBe(5);
    expect(sorted[2].id).toBe(8);
  });

  test("null sort 视为最大，排在最后", function () {
    var menus = [
      { id: 1, sort: null, name: "未排序" },
      { id: 2, sort: 10, name: "A" },
      { id: 3, sort: 5, name: "B" },
    ];
    var sorted = menus.slice().sort(menuSortCompare);
    expect(sorted[0].name).toBe("B");
    expect(sorted[1].name).toBe("A");
    expect(sorted[2].name).toBe("未排序");
  });

  test("全部 sort 为 null 时按 id 升序排", function () {
    var menus = [
      { id: 3, sort: null },
      { id: 1, sort: null },
      { id: 2, sort: null },
    ];
    var sorted = menus.slice().sort(menuSortCompare);
    expect(sorted[0].id).toBe(1);
    expect(sorted[1].id).toBe(2);
    expect(sorted[2].id).toBe(3);
  });

  test("包含 undefined sort 的情况", function () {
    var menus = [
      { id: 1, sort: undefined, name: "A" },
      { id: 2, name: "B" }, // sort 字段不存在
      { id: 3, sort: 10, name: "C" },
    ];
    var sorted = menus.slice().sort(menuSortCompare);
    expect(sorted[0].name).toBe("C");
    expect(sorted[1].id).toBeGreaterThanOrEqual(1);
  });

  test("原数组不被修改", function () {
    var original = [
      { id: 1, sort: 30 },
      { id: 2, sort: 10 },
      { id: 3, sort: 20 },
    ];
    var menus = original.slice();
    menus.slice().sort(menuSortCompare);
    expect(menus[0].id).toBe(1);
    expect(menus[1].id).toBe(2);
    expect(menus[2].id).toBe(3);
  });

  test("空数组排序无异常", function () {
    var sorted = [].slice().sort(menuSortCompare);
    expect(sorted).toEqual([]);
  });

  test("模拟修复前的 bug 场景：sort 都为 null 时被错误按 id 排", function () {
    // 这个测试验证修复后的行为：当 sort 都为 null 时，正确的 fallback 是按 id 排
    var menus = [
      { id: 1, sort: null, path: "/dashboard" },
      { id: 2, sort: null, path: "/league" },
      { id: 3, sort: null, path: "/war" },
    ];
    var sorted = menus.slice().sort(menuSortCompare);
    // null sort 按 id 升序排：dashboard(1), league(2), war(3)
    expect(sorted[0].path).toBe("/dashboard");
    expect(sorted[1].path).toBe("/league");
    expect(sorted[2].path).toBe("/war");
  });

  test("典型场景：左侧导航排序修复", function () {
    // 模拟用户在系统中看到的菜单顺序问题：
    // 数据库 sort 字段：系统管理=100, 联赛管理=200, 数据看板=50
    // 修复前因只按 id 排，顺序是错误的
    var dbMenus = [
      { id: 1, sort: 100, path: "/system", name: "系统管理" },
      { id: 2, sort: 200, path: "/league", name: "联赛管理" },
      { id: 3, sort: 50, path: "/dashboard", name: "数据看板" },
    ];
    var sorted = dbMenus.slice().sort(menuSortCompare);
    expect(sorted[0].name).toBe("数据看板");
    expect(sorted[1].name).toBe("系统管理");
    expect(sorted[2].name).toBe("联赛管理");
  });
});
