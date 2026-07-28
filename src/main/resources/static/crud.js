/* 通用 CRUD 页面工厂：传入列配置即可生成带查询/分页/新增/编辑/删除的表格页 */
(function () {
  function clean(obj) {
    const o = {};
    Object.keys(obj).forEach((k) => {
      const v = obj[k];
      if (v !== "" && v !== null && v !== undefined) o[k] = v;
    });
    return o;
  }

  // 通过 baseUrl + cols 生成一个 Vue 组件对象
  window.createCrud = function (opts) {
    return {
      name: opts.name || "CrudPage",
      data() {
        return {
          baseUrl: opts.baseUrl,
          listUrl: opts.listUrl || opts.baseUrl + "/list", // 列表接口地址
          cols: opts.cols,
          keyword: "",
          filters: {},
          sortField: "", // 当前排序列（prop 名）
          sortOrder: "", // 'asc' | 'desc' | ''
          list: [],
          total: 0,
          page: 1,
          size: 10,
          loading: false,
          dialogVisible: false,
          dialogTitle: "新增",
          form: {},
          dictOptions: {}, // { groupCode: [{label,value}] }
          remoteOptions: {}, // { propName: [{label,value}] } - 远程搜索下拉的候选
          extraButtons: opts.extraButtons || [], // 额外按钮（如"一键初始化报名"）
          initDialogVisible: false, // 初始化报名对话框
          initLeagueNo: "", // 初始化报名选中的联赛
          initClanNo: "", // 初始化报名选中的部落
          initLoading: false, // 初始化报名执行中
          remoteLoading: {}, // { propName: boolean }
          preset: opts.preset || {}, // 固定查询条件（如字典项按群组过滤）
        };
      },
      computed: {
        searchCols() {
          return this.cols.filter((c) => c.search);
        },
        tableCols() {
          return this.cols.filter((c) => !c.hideInTable);
        },
        formCols() {
          return this.cols.filter((c) => !c.hideInForm);
        },
        isListView() {
          return !!opts.listMode;
        },
        // 各写操作对应的菜单权限标识（在 crud-instances.js 的 opts.perms 中配置）。
        // 未配置时（perm 为空）该按钮不做权限拦截，默认可见。
        createPerm() {
          return opts.perms && opts.perms.create;
        },
        editPerm() {
          return opts.perms && opts.perms.edit;
        },
        deletePerm() {
          return opts.perms && opts.perms.delete;
        },
        // 查询/重置按钮不做控制；其它按钮按菜单里配置的权限标识控制。
        canCreate() {
          return !this.createPerm || COC.store.hasPerm(this.createPerm);
        },
        canEdit() {
          return !this.editPerm || COC.store.hasPerm(this.editPerm);
        },
        canDelete() {
          return !this.deletePerm || COC.store.hasPerm(this.deletePerm);
        },
        // 经过权限过滤后可见的额外按钮（每个 extraButton 可带 perm 字段）
        visibleExtraButtons() {
          return (opts.extraButtons || []).filter(
            (b) => !b.perm || COC.store.hasPerm(b.perm),
          );
        },
      },
      async mounted() {
        await this.loadDicts();
        // 预拉取所有 remote-select 字段的默认候选，供表格列把 id 翻译为 label
        await this.preloadRemoteOptions();
        // 预拉取所有 tree-select 字段的全量数据并转树
        await this.preloadTreeOptions();
        // 列表模式下若需要先选条件才加载，可设置 opts.lazy=true 跳过首次加载
        if (!opts.lazy) await this.load();
      },
      methods: {
        async loadDicts() {
          const dictCols = this.cols.filter((c) => c.dictCode);
          await Promise.all(
            dictCols.map(async (c) => {
              try {
                const r = await COC.api.dictItems(c.dictCode);
                this.dictOptions[c.dictCode] = (r.records || []).map((i) => ({
                  label: i.itemName,
                  // 将纯数字字串规范化为 Number，使 el-select 与表单绑定值类型一致，回显中文
                  value:
                    typeof i.itemValue === "string" &&
                    /^-?\d+(\.\d+)?$/.test(i.itemValue.trim())
                      ? Number(i.itemValue.trim())
                      : i.itemValue,
                }));
              } catch (e) {
                /* 无权限或字典未初始化 */
              }
            }),
          );
        },
        /** 预拉取所有远程下拉的默认候选，让表格列能把 id 翻译为 label */
        async preloadRemoteOptions() {
          const remoteCols = this.cols.filter(
            (c) => c.type === "remote-select",
          );
          await Promise.all(remoteCols.map((c) => this.remoteSearch(c, "")));
        },
        /** 预拉取所有 tree-select 列的全量数据并转树，存入 dictOptions 复用 */
        async preloadTreeOptions() {
          const treeCols = this.cols.filter(
            (c) => c.type === "tree-select" && c.url,
          );
          await Promise.all(
            treeCols.map(async (c) => {
              try {
                const r = await COC.api.page(c.url, { size: 9999 });
                const records = r.records || [];
                // 表格列翻译（id → 菜单名）也复用这份数据
                const idToLabel = {};
                records.forEach((m) => {
                  idToLabel[m.id] = m.menuName || m.name;
                });
                this.dictOptions[c.prop] = idToLabel;
                // 把列表转成树
                const map = new Map();
                records.forEach((m) =>
                  map.set(m.id, {
                    id: m.id,
                    parentId: m.parentId || 0,
                    label: m.menuName || m.name,
                    children: [],
                  }),
                );
                const roots = [];
                map.forEach((node) => {
                  if (node.parentId && map.has(node.parentId)) {
                    map.get(node.parentId).children.push(node);
                  } else {
                    roots.push(node);
                  }
                });
                // 给"无父"选项预留"顶级菜单"占位（id=0）
                this.dictOptions[c.prop + "Tree"] = [
                  { id: 0, label: "顶级菜单", children: roots },
                ];
              } catch (e) {
                /* 无权限或接口缺失 */
              }
            }),
          );
        },
        async load() {
          this.loading = true;
          try {
            if (this.isListView) {
              // 列表模式：调用 /list 接口，返回数组
              var params = Object.assign({}, this.filters, this.preset);
              var r = await COC.api.get(this.listUrl, clean(params));
              this.list = Array.isArray(r) ? r : r.records || [];
              this.total = this.list.length;
            } else {
              var params2 = Object.assign(
                { keyword: this.keyword, current: this.page, size: this.size },
                this.filters,
                this.preset,
              );
              if (this.sortField) {
                params2.sortField = this.sortField;
                params2.sortOrder = this.sortOrder || "asc";
              }
              var r2 = await COC.api.page(this.baseUrl, clean(params2));
              this.list = r2.records || [];
              this.total = r2.total || 0;
            }
          } finally {
            this.loading = false;
          }
        },
        onSearch() {
          this.page = 1;
          this.load();
        },
        onReset() {
          this.keyword = "";
          this.filters = {};
          this.sortField = "";
          this.sortOrder = "";
          this.page = 1;
          this.load();
        },
        /** 表头排序变化（仅 sortable:'custom' 列触发）：向分页接口传递排序参数 */
        onSortChange({ prop, order }) {
          this.sortField = order ? prop : "";
          this.sortOrder = order === "descending" ? "desc" : "asc";
          this.page = 1;
          this.load();
        },
        optionsFor(c) {
          if (typeof c.options === "function") return c.options() || [];
          if (c.options) return c.options;
          if (c.dictCode) return this.dictOptions[c.dictCode] || [];
          if (c.type === "remote-select")
            return this.remoteOptions[c.prop] || [];
          if (c.type === "tree-select")
            return this.dictOptions[c.prop + "Tree"] || [];
          return [];
        },
        labelOf(c, val) {
          // 树形数据（tree-select）走 idToLabel 翻译
          if (c.type === "tree-select" && val !== "" && val != null) {
            return (
              (this.dictOptions[c.prop] && this.dictOptions[c.prop][val]) || val
            );
          }
          const o = this.optionsFor(c).find(
            (x) => String(x.value) === String(val),
          );
          return o ? o.label : val;
        },
        defaultForm() {
          const f = {};
          this.formCols.forEach((c) => {
            if (c.type === "switch")
              f[c.prop] = c.default !== undefined ? c.default : 1;
            else if (c.type === "number")
              f[c.prop] = c.default !== undefined ? c.default : 0;
            else {
              // 字典类下拉的选项 value 在 loadDicts 中已被规范为 Number，
              // 若默认值仍是纯数字串（如 "1"），需同步转成 Number 才能匹配选项回显中文
              let val = c.default !== undefined ? c.default : "";
              if (
                c.dictCode &&
                typeof val === "string" &&
                /^-?\d+(\.\d+)?$/.test(val.trim())
              ) {
                val = Number(val.trim());
              }
              f[c.prop] = val;
            }
          });
          return f;
        },
        openCreate() {
          this.dialogTitle = "新增";
          this.form = this.defaultForm();
          this.dialogVisible = true;
          // 预拉取远程下拉的默认项，避免首次打开无候选
          this.formCols
            .filter((c) => c.type === "remote-select")
            .forEach((c) => {
              if (!this.remoteOptions[c.prop]) this.remoteSearch(c, "");
            });
          this.$nextTick(
            () => this.$refs.formRef && this.$refs.formRef.clearValidate(),
          );
        },
        openEdit(row) {
          this.dialogTitle = "编辑";
          const f = this.defaultForm();
          // 保留 id 用于 submit 判断编辑状态及提交给后端 update 接口
          if (row.id !== undefined && row.id !== null) f.id = row.id;
          this.formCols.forEach((c) => {
            let v = row[c.prop];
            if (c.type === "switch") v = v == null ? 1 : Number(v);
            if (c.type === "remote-select" && (v === "" || v === undefined))
              v = null;
            f[c.prop] = v;
          });
          this.form = f;
          this.dialogVisible = true;
          // 编辑时拉一次候选，保证当前值可显示在选项中
          this.formCols
            .filter((c) => c.type === "remote-select")
            .forEach((c) => {
              this.remoteSearch(c, "");
            });
          this.$nextTick(
            () => this.$refs.formRef && this.$refs.formRef.clearValidate(),
          );
        },
        /** 远程下拉搜索：根据输入的关键词请求分页接口，刷新候选列表 */
        async remoteSearch(c, query) {
          // Vue3 中 data 返回的对象为 reactive，直接赋值即可触发响应式更新
          this.remoteLoading[c.prop] = true;
          try {
            const params = { size: c.pageSize || 20 };
            const kw = (query || "").trim();
            if (c.searchKey) params[c.searchKey] = kw;
            else params.keyword = kw;
            const r = await COC.api.page(c.url, params);
            this.remoteOptions[c.prop] = (r.records || []).map((x) => {
              const opt = {
                label: c.labelKey ? x[c.labelKey] : x.label,
                value: c.valueKey ? x[c.valueKey] : x.id,
              };
              if (c.extraFields) {
                c.extraFields.forEach((f) => { opt[f] = x[f]; });
              }
              return opt;
            });
          } catch (e) {
            /* 已在拦截器提示 */
          } finally {
            this.remoteLoading[c.prop] = false;
          }
        },
        // 下拉选择联动：选中后把关联字段（如 memberNo）带出到表单
        onRemoteSelectChange(c, val) {
          if (!c.fillProps) return;
          const opt = (this.remoteOptions[c.prop] || []).find((o) => o.value === val);
          Object.keys(c.fillProps).forEach((target) => {
            const source = c.fillProps[target];
            if (opt && opt[source] != null) {
              this.form[target] = opt[source];
            }
          });
        },
        submit() {
          this.$refs.formRef.validate(async (valid) => {
            if (!valid) return;
            const payload = {};
            // 编辑时把 id 带入 payload，供后端 update 接口定位记录
            if (
              this.form.id !== undefined &&
              this.form.id !== null &&
              this.form.id !== ""
            ) {
              payload.id = this.form.id;
            }
            this.formCols.forEach((c) => {
              let v = this.form[c.prop];
              // 远程下拉的空值统一转为 null，避免 Long 类型字段收到空串
              if (c.type === "remote-select" && (v === "" || v === undefined))
                v = null;
              payload[c.prop] = v;
            });
            try {
              if (payload.id) await COC.api.update(this.baseUrl, payload);
              else await COC.api.create(this.baseUrl, payload);
              ElementPlus.ElMessage.success("保存成功");
              this.dialogVisible = false;
              this.load();
              // 数据变更后，tree-select 字段的候选树可能过期（如新增菜单后应出现在父菜单下拉中），重新加载
              this.preloadTreeOptions();
            } catch (e) {
              /* 错误已在拦截器提示 */
            }
          });
        },
        remove(row) {
          ElementPlus.ElMessageBox.confirm("确定要删除该记录吗？", "提示", {
            type: "warning",
          })
            .then(async () => {
              await COC.api.remove(this.baseUrl, row.id);
              ElementPlus.ElMessage.success("删除成功");
              this.load();
              // 删除后同样刷新 tree-select 候选，避免下拉树残留已删除项
              this.preloadTreeOptions();
            })
            .catch(() => {});
        },
      },
      template: `
      <div>
        <el-form inline @submit.prevent class="coc-toolbar">
          <slot name="search-extras"></slot>
          <el-form-item v-for="c in searchCols" :key="c.prop" :label="c.label">
            <el-select v-if="c.type==='select'" v-model="filters[c.prop]" clearable :placeholder="'请选择'+c.label" style="width:160px">
              <el-option v-for="o in optionsFor(c)" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-select v-else-if="c.type==='remote-select' && !c.searchAsText" v-model="filters[c.prop]" clearable filterable :placeholder="'请选择'+c.label" style="width:180px">
              <el-option v-for="o in (remoteOptions[c.prop]||[])" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-select v-else-if="c.type==='switch'" v-model="filters[c.prop]" clearable :placeholder="'请选择'+c.label" style="width:120px">
              <el-option label="是" :value="1" />
              <el-option label="否" :value="0" />
            </el-select>
            <el-input v-else v-model="filters[c.prop]" :placeholder="'请输入'+c.label" clearable style="width:180px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
            <el-button v-if="canCreate" type="success" @click="openCreate">新增</el-button>
            <el-button v-for="b in visibleExtraButtons" :key="b.click" :type="b.type||'default'" @click="this[b.click]()">{{ b.text }}</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="list" v-loading="loading" border stripe style="width:100%" @sort-change="onSortChange">
          <el-table-column type="index" label="#" width="50" />
          <template v-for="c in tableCols" :key="c.prop">
            <el-table-column :prop="c.prop" :label="c.label" :width="c.width" :min-width="c.minWidth" :sortable="c.sortable ? 'custom' : undefined">
              <template #default="{ row }" v-if="c.type==='switch'">
                <el-tag :type="row[c.prop]==1 ? 'success' : 'info'">
                  {{ row[c.prop]==1 ? (c.activeText||'是') : (c.inactiveText||'否') }}
                </el-tag>
              </template>
              <template #default="{ row }" v-else-if="c.extraProp && c.extraPropFirst">
                <div>{{ row[c.extraProp] || '-' }}</div>
                <div style="color:#909399; font-size:12px; margin-top:2px;">{{ row[c.prop] || '-' }}</div>
              </template>
              <template #default="{ row }" v-else-if="c.extraProp">
                <div>{{ row[c.prop] || '-' }}</div>
                <div style="color:#909399; font-size:12px; margin-top:2px;">{{ row[c.extraProp] || '-' }}</div>
              </template>
              <template #default="{ row }" v-else-if="c.formatter">
                {{ c.formatter(row) }}
              </template>
              <template #default="{ row }" v-else-if="(c.dictCode || c.options || c.type==='remote-select')">
                {{ labelOf(c, row[c.prop]) }}
              </template>
            </el-table-column>
          </template>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="canEdit || canDelete">
                <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
              </template>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>

        <div style="display:flex;justify-content:flex-end;margin-top:12px" v-if="!isListView">
          <el-pagination
            v-model:current-page="page" v-model:page-size="size"
            :total="total" :page-sizes="[10,20,50,100]"
            layout="total, sizes, prev, pager, next, jumper" background
            @current-change="load" @size-change="onSearch" />
        </div>

        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" destroy-on-close>
          <el-form ref="formRef" :model="form" label-width="110px">
            <el-form-item v-for="c in formCols" :key="c.prop" :label="c.label" :prop="c.prop" :rules="c.rule">
              <el-select v-if="c.type==='select'" v-model="form[c.prop]" clearable :placeholder="'请选择'+c.label" style="width:100%">
                <el-option v-for="o in optionsFor(c)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-select v-else-if="c.type==='remote-select'" v-model="form[c.prop]"
                filterable remote clearable
                :remote-method="(q) => remoteSearch(c, q)"
                :loading="!!remoteLoading[c.prop]"
                :allow-create="!!c.allowCreate"
                :placeholder="c.placeholder || ('请输入关键词搜索'+c.label)"
                @change="(v) => onRemoteSelectChange(c, v)"
                style="width:100%">
                <el-option v-for="o in (remoteOptions[c.prop] || [])" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-tree-select v-else-if="c.type==='tree-select'" v-model="form[c.prop]"
                :data="optionsFor(c)"
                :props="c.treeProps || { value: 'id', label: 'label', children: 'children' }"
                node-key="id"
                clearable check-strictly
                :placeholder="c.placeholder || ('请选择'+c.label)"
                style="width:100%" />
              <el-switch v-else-if="c.type==='switch'" v-model="form[c.prop]" :active-value="1" :inactive-value="0" />
              <el-input v-else-if="c.type==='textarea'" v-model="form[c.prop]" type="textarea" :rows="3" :placeholder="'请输入'+c.label" />
              <el-date-picker v-else-if="c.type==='date'" v-model="form[c.prop]" type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择时间" style="width:100%" />
              <el-date-picker v-else-if="c.type==='date-ymd'" v-model="form[c.prop]" type="date"
                value-format="YYYYMMDD" format="YYYYMMDD" placeholder="选择日期" style="width:100%"
                :disabled="c.disabled || (c.disabledOnEdit && !!form.id)" />
              <el-input-number v-else-if="c.type==='number'" v-model="form[c.prop]" :min="0" controls-position="right" style="width:100%" />
              <el-input v-else v-model="form[c.prop]" :placeholder="'请输入'+c.label" :disabled="c.disabled" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible=false">取消</el-button>
            <el-button type="primary" @click="submit">确定</el-button>
          </template>
        </el-dialog>
        <el-dialog v-if="extraButtons.length" v-model="initDialogVisible" title="一键初始化报名数据" width="440px">
          <el-form label-width="90px">
            <el-form-item label="所属联赛" required>
              <el-select v-model="initLeagueNo" filterable clearable placeholder="请选择联赛" style="width:100%">
                <el-option v-for="o in (remoteOptions.leagueNo||[])" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="所属部落" required>
              <el-select v-model="initClanNo" filterable clearable placeholder="请选择部落" style="width:100%">
                <el-option v-for="o in (remoteOptions.clanNo||[])" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-form>
          <div style="color:#909399;font-size:13px;line-height:1.8;">
            初始化规则：<br/>
            1. 查询该部落在组状态为"已加入"的成员<br/>
            2. 成员默认参战"是"→备选报名，"否"→未报名<br/>
            3. 已存在且为"主动报名"的记录跳过，其余先删再插
          </div>
          <template #footer>
            <el-button @click="initDialogVisible=false">取消</el-button>
            <el-button type="primary" :loading="initLoading" @click="doInitSignup">确认初始化</el-button>
          </template>
        </el-dialog>
        ${opts.extraTemplate || ""}
      </div>`,
    };
  };

  /**
   * 菜单树管理工厂：替代通用 CRUD 列表，适合菜单这种层级数据结构。
   * 特性：
   *   - 树视图展示系统所有菜单
   *   - 关键字过滤（按名称/路径/权限标识，匹配节点的父链保留可见）
   *   - 每行支持 新增子菜单 / 编辑 / 删除（删除调用 cascade 接口级联删）
   *   - 表单支持目录/菜单/按钮 三种类型，按类型动态渲染字段
   *   - 类型标签：目录=success, 菜单=primary, 按钮=info
   */
  window.createMenuTree = function () {
    /** 创建空的菜单表单对象：parentId=null 表示顶级菜单 */
    const makeEmptyForm = (parentId) => ({
      id: null,
      parentId: parentId == null ? 0 : parentId,
      menuName: "",
      menuType: parentId == null ? 0 : 1,
      path: "",
      component: "",
      icon: "",
      permission: "",
      sort: 0,
    });
    return {
      name: "MenuTree",
      data() {
        return {
          treeData: [],
          keyword: "",
          loading: false,
          dialogVisible: false,
          dialogTitle: "新增菜单",
          saving: false,
          form: makeEmptyForm(null),
          treeProps: {
            children: "children",
            label: (data, node) => data.menuName || "（未命名）",
          },
        };
      },
      computed: {
        /**
         * 关键字过滤后的树视图：
         * 1) 空关键字 → 返回原树（O(1) 引用，性能友好）
         * 2) 非空关键字 → 深度优先递归过滤，匹配的节点及其所有祖先链保留，
         *    不可见子节点被截断。这是 Element Plus el-tree 推荐的"路径保留"过滤方式。
         * 匹配规则：菜单名 / 路由路径 / 权限标识 任一命中即视为匹配。
         */
        filteredData() {
          if (!this.keyword || !this.keyword.trim()) return this.treeData;
          const kw = this.keyword.trim().toLowerCase();
          const walk = (nodes) => {
            const out = [];
            for (const n of nodes) {
              const children = Array.isArray(n.children) ? n.children : [];
              const childrenOut = walk(children);
              const match =
                (n.menuName || "").toLowerCase().includes(kw) ||
                (n.path || "").toLowerCase().includes(kw) ||
                (n.permission || "").toLowerCase().includes(kw);
              if (match || childrenOut.length > 0) {
                out.push(Object.assign({}, n, { children: childrenOut }));
              }
            }
            return out;
          };
          return walk(this.treeData);
        },
      },
      methods: {
        makeEmptyForm,
        /** 加载菜单树：调用 /api/sys/menu/tree */
        async loadTree() {
          this.loading = true;
          try {
            const data = await COC.api.get("/api/sys/menu/tree");
            this.treeData = Array.isArray(data) ? data : [];
          } catch (e) {
            if (window.ElementPlus && ElementPlus.ElMessage) {
              ElementPlus.ElMessage.error(
                "加载菜单失败：" + ((e && e.message) || ""),
              );
            }
            this.treeData = [];
          } finally {
            this.loading = false;
          }
        },
        /** 类型标签显示文本（兼容 null） */
        typeLabel(t) {
          return ["目录", "菜单", "按钮"][t == null ? 1 : t];
        },
        /** 类型标签 Element Plus tag 类型 */
        typeTagType(t) {
          return ["success", "primary", "info"][t == null ? 1 : t];
        },
        /** 新增：parentId 为 null/falsy 表示顶级菜单 */
        openCreate(parentId) {
          this.dialogTitle = parentId ? "新增子菜单" : "新增顶级菜单";
          this.form = makeEmptyForm(parentId || null);
          this.dialogVisible = true;
        },
        /** 编辑：把后端节点映射为表单字段（缺失字段兜底默认） */
        openEdit(node) {
          this.dialogTitle = "编辑菜单";
          this.form = {
            id: node.id,
            parentId: node.parentId || 0,
            menuName: node.menuName || "",
            menuType: node.menuType == null ? 1 : node.menuType,
            path: node.path || "",
            component: node.component || "",
            icon: node.icon || "",
            permission: node.permission || "",
            sort: node.sort || 0,
          };
          this.dialogVisible = true;
        },
        /**
         * 级联删除（前端预收集所有后代，调 cascade 接口）：
         * 后端 cascade 接口会一并清理 sys_role_menu 关联 + 软删所有后代。
         */
        async onDelete(node) {
          try {
            if (!window.ElementPlus) return;
            await ElementPlus.ElMessageBox.confirm(
              `确定删除菜单"${node.menuName}"及其所有子菜单？此操作不可撤销。`,
              "确认删除",
              { type: "warning" },
            );
          } catch (e) {
            return;
          }
          this.loading = true;
          try {
            await COC.api.remove("/api/sys/menu/cascade", node.id);
            if (ElementPlus.ElMessage)
              ElementPlus.ElMessage.success("删除成功");
            await this.loadTree();
          } catch (e) {
            if (ElementPlus.ElMessage)
              ElementPlus.ElMessage.error(
                "删除失败：" + ((e && e.message) || ""),
              );
          } finally {
            this.loading = false;
          }
        },
        /**
         * 保存：基础校验 → 调用 create/update → 重新加载。
         * 校验规则与表结构对齐：
         *  - menuName 必填
         *  - menuType=0/1 必填 path（路由入口）
         *  - menuType=1/2 必填 permission（接口鉴权标识）
         */
        async onSave() {
          if (!this.form.menuName) {
            if (window.ElementPlus)
              ElementPlus.ElMessage.warning("请输入菜单名称");
            return;
          }
          if (this.form.menuType !== 2 && !this.form.path) {
            if (window.ElementPlus)
              ElementPlus.ElMessage.warning("请输入路由路径");
            return;
          }
          if (
            (this.form.menuType === 1 || this.form.menuType === 2) &&
            !this.form.permission
          ) {
            if (window.ElementPlus)
              ElementPlus.ElMessage.warning("请输入权限标识");
            return;
          }
          this.saving = true;
          try {
            if (this.form.id) {
              await COC.api.update("/api/sys/menu", this.form);
            } else {
              await COC.api.create("/api/sys/menu", this.form);
            }
            if (window.ElementPlus) ElementPlus.ElMessage.success("保存成功");
            this.dialogVisible = false;
            await this.loadTree();
          } catch (e) {
            if (window.ElementPlus)
              ElementPlus.ElMessage.error(
                "保存失败：" + ((e && e.message) || ""),
              );
          } finally {
            this.saving = false;
          }
        },
      },
      mounted() {
        this.loadTree();
      },
      template: `
        <div class="coc-menu-tree">
          <div class="coc-mt-toolbar">
            <el-input v-model="keyword" placeholder="按名称 / 路径 / 权限搜索" clearable
              style="width:280px;" />
            <el-button v-if="COC.store.hasPerm('sys:menu:add')" type="primary" @click="openCreate(null)" style="margin-left:12px;">新增顶级菜单</el-button>
            <el-button @click="loadTree" style="margin-left:8px;">刷新</el-button>
            <span class="coc-mt-tip">共 {{ treeData.length }} 个根菜单，关键字过滤时父链自动保留</span>
          </div>
          <div v-loading="loading" class="coc-mt-wrap">
            <el-tree
              :data="filteredData"
              :props="treeProps"
              node-key="id"
              :default-expand-all="!keyword"
              :expand-on-click-node="false"
              empty-text="暂无菜单">
              <template #default="{ node, data }">
                <span class="coc-mt-node">
                  <span class="coc-mt-label">
                    <el-tag size="small" :type="typeTagType(data.menuType)">{{ typeLabel(data.menuType) }}</el-tag>
                    <span style="margin:0 8px;font-weight:500;">{{ data.menuName }}</span>
                    <span v-if="data.path" class="coc-mt-path">{{ data.path }}</span>
                    <span v-if="data.permission" class="coc-mt-perm">@{{ data.permission }}</span>
                  </span>
                  <span class="coc-mt-actions">
                    <el-button v-if="COC.store.hasPerm('sys:menu:add')" link size="small" type="primary" @click.stop="openCreate(data.id)">新增子菜单</el-button>
                    <el-button v-if="COC.store.hasPerm('sys:menu:edit')" link size="small" type="primary" @click.stop="openEdit(data)">编辑</el-button>
                    <el-button v-if="COC.store.hasPerm('sys:menu:delete')" link size="small" type="danger" @click.stop="onDelete(data)">删除</el-button>
                  </span>
                </span>
              </template>
            </el-tree>
          </div>
          <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" :close-on-click-modal="false" destroy-on-close>
            <el-form :model="form" label-width="100px">
              <el-form-item label="父菜单">
                <el-tree-select v-model="form.parentId" :data="treeData"
                  :props="{ value: 'id', label: 'menuName', children: 'children' }"
                  check-strictly :render-after-expand="false"
                  node-key="id" clearable
                  placeholder="（不选则为顶级菜单）" style="width:100%;" />
              </el-form-item>
              <el-form-item label="菜单名称" required>
                <el-input v-model="form.menuName" placeholder="例如：用户管理" />
              </el-form-item>
              <el-form-item label="类型" required>
                <el-radio-group v-model="form.menuType">
                  <el-radio :value="0">目录</el-radio>
                  <el-radio :value="1">菜单</el-radio>
                  <el-radio :value="2">按钮</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="路由路径" v-if="form.menuType !== 2" required>
                <el-input v-model="form.path" placeholder="/sys/menu" />
              </el-form-item>
              <el-form-item label="组件路径" v-if="form.menuType === 1">
                <el-input v-model="form.component" placeholder="views/SystemMenu.vue" />
              </el-form-item>
              <el-form-item label="图标" v-if="form.menuType !== 2">
                <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 Setting" />
              </el-form-item>
              <el-form-item label="权限标识" v-if="form.menuType === 1 || form.menuType === 2" required>
                <el-input v-model="form.permission" placeholder="module:action，例如 clan:add" />
              </el-form-item>
              <el-form-item label="排序">
                <el-input-number v-model="form.sort" :min="0" controls-position="right" style="width:100%;" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="dialogVisible = false">取消</el-button>
              <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
            </template>
          </el-dialog>
        </div>`,
    };
  };
})();
