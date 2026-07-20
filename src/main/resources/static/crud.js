/* 通用 CRUD 页面工厂：传入列配置即可生成带查询/分页/新增/编辑/删除的表格页 */
(function () {
  function clean(obj) {
    const o = {};
    Object.keys(obj).forEach((k) => {
      const v = obj[k];
      if (v !== '' && v !== null && v !== undefined) o[k] = v;
    });
    return o;
  }

  // 通过 baseUrl + cols 生成一个 Vue 组件对象
  window.createCrud = function (opts) {
    return {
      name: opts.name || 'CrudPage',
      data() {
        return {
          baseUrl: opts.baseUrl,
          listUrl: opts.listUrl || (opts.baseUrl + '/list'), // 列表接口地址
          cols: opts.cols,
          keyword: '',
          filters: {},
          list: [],
          total: 0,
          page: 1,
          size: 10,
          loading: false,
          dialogVisible: false,
          dialogTitle: '新增',
          form: {},
          dictOptions: {}, // { groupCode: [{label,value}] }
          remoteOptions: {}, // { propName: [{label,value}] } - 远程搜索下拉的候选
          remoteLoading: {}, // { propName: boolean }
          preset: opts.preset || {} // 固定查询条件（如字典项按群组过滤）
        };
      },
      computed: {
        searchCols() { return this.cols.filter((c) => c.search); },
        tableCols() { return this.cols.filter((c) => !c.hideInTable); },
        formCols() { return this.cols.filter((c) => !c.hideInForm); },
        isListView() { return !!opts.listMode; }
      },
      async mounted() {
        await this.loadDicts();
        // 预拉取所有 remote-select 字段的默认候选，供表格列把 id 翻译为 label
        await this.preloadRemoteOptions();
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
                  value: i.itemValue
                }));
              } catch (e) { /* 无权限或字典未初始化 */ }
            })
          );
        },
        /** 预拉取所有远程下拉的默认候选，让表格列能把 id 翻译为 label */
        async preloadRemoteOptions() {
          const remoteCols = this.cols.filter((c) => c.type === 'remote-select');
          await Promise.all(remoteCols.map((c) => this.remoteSearch(c, '')));
        },
        async load() {
          this.loading = true;
          try {
            if (this.isListView) {
              // 列表模式：调用 /list 接口，返回数组
              var params = Object.assign({}, this.filters, this.preset);
              var r = await COC.api.get(this.listUrl, clean(params));
              this.list = Array.isArray(r) ? r : (r.records || []);
              this.total = this.list.length;
            } else {
              var params2 = Object.assign(
                { keyword: this.keyword, current: this.page, size: this.size },
                this.filters,
                this.preset
              );
              var r2 = await COC.api.page(this.baseUrl, clean(params2));
              this.list = r2.records || [];
              this.total = r2.total || 0;
            }
          } finally {
            this.loading = false;
          }
        },
        onSearch() { this.page = 1; this.load(); },
        onReset() { this.keyword = ''; this.filters = {}; this.page = 1; this.load(); },
        optionsFor(c) {
          if (typeof c.options === 'function') return c.options() || [];
          if (c.options) return c.options;
          if (c.dictCode) return this.dictOptions[c.dictCode] || [];
          if (c.type === 'remote-select') return this.remoteOptions[c.prop] || [];
          return [];
        },
        labelOf(c, val) {
          const o = this.optionsFor(c).find((x) => String(x.value) === String(val));
          return o ? o.label : val;
        },
        defaultForm() {
          const f = {};
          this.formCols.forEach((c) => {
            if (c.type === 'switch') f[c.prop] = c.default !== undefined ? c.default : 1;
            else if (c.type === 'number') f[c.prop] = c.default !== undefined ? c.default : 0;
            else f[c.prop] = c.default !== undefined ? c.default : '';
          });
          return f;
        },
        openCreate() {
          this.dialogTitle = '新增';
          this.form = this.defaultForm();
          this.dialogVisible = true;
          // 预拉取远程下拉的默认项，避免首次打开无候选
          this.formCols.filter((c) => c.type === 'remote-select').forEach((c) => {
            if (!this.remoteOptions[c.prop]) this.remoteSearch(c, '');
          });
          this.$nextTick(() => this.$refs.formRef && this.$refs.formRef.clearValidate());
        },
        openEdit(row) {
          this.dialogTitle = '编辑';
          const f = this.defaultForm();
          // 保留 id 用于 submit 判断编辑状态及提交给后端 update 接口
          if (row.id !== undefined && row.id !== null) f.id = row.id;
          this.formCols.forEach((c) => {
            let v = row[c.prop];
            if (c.type === 'switch') v = v == null ? 1 : Number(v);
            if (c.type === 'remote-select' && (v === '' || v === undefined)) v = null;
            f[c.prop] = v;
          });
          this.form = f;
          this.dialogVisible = true;
          // 编辑时拉一次候选，保证当前值可显示在选项中
          this.formCols.filter((c) => c.type === 'remote-select').forEach((c) => {
            this.remoteSearch(c, '');
          });
          this.$nextTick(() => this.$refs.formRef && this.$refs.formRef.clearValidate());
        },
        /** 远程下拉搜索：根据输入的关键词请求分页接口，刷新候选列表 */
        async remoteSearch(c, query) {
          // Vue3 中 data 返回的对象为 reactive，直接赋值即可触发响应式更新
          this.remoteLoading[c.prop] = true;
          try {
            const params = { size: c.pageSize || 20 };
            const kw = (query || '').trim();
            if (c.searchKey) params[c.searchKey] = kw;
            else params.keyword = kw;
            const r = await COC.api.page(c.url, params);
            this.remoteOptions[c.prop] = (r.records || []).map((x) => ({
              label: c.labelKey ? x[c.labelKey] : x.label,
              value: c.valueKey ? x[c.valueKey] : x.id
            }));
          } catch (e) { /* 已在拦截器提示 */ }
          finally { this.remoteLoading[c.prop] = false; }
        },
        submit() {
          this.$refs.formRef.validate(async (valid) => {
            if (!valid) return;
            const payload = {};
            // 编辑时把 id 带入 payload，供后端 update 接口定位记录
            if (this.form.id !== undefined && this.form.id !== null && this.form.id !== '') {
              payload.id = this.form.id;
            }
            this.formCols.forEach((c) => {
              let v = this.form[c.prop];
              // 远程下拉的空值统一转为 null，避免 Long 类型字段收到空串
              if (c.type === 'remote-select' && (v === '' || v === undefined)) v = null;
              payload[c.prop] = v;
            });
            try {
              if (payload.id) await COC.api.update(this.baseUrl, payload);
              else await COC.api.create(this.baseUrl, payload);
              ElementPlus.ElMessage.success('保存成功');
              this.dialogVisible = false;
              this.load();
            } catch (e) { /* 错误已在拦截器提示 */ }
          });
        },
        remove(row) {
          ElementPlus.ElMessageBox.confirm('确定要删除该记录吗？', '提示', {
            type: 'warning'
          }).then(async () => {
            await COC.api.remove(this.baseUrl, row.id);
            ElementPlus.ElMessage.success('删除成功');
            this.load();
          }).catch(() => {});
        }
      },
      template: `
      <div>
        <el-form inline @submit.prevent class="coc-toolbar">
          <el-form-item label="关键字">
            <el-input v-model="keyword" placeholder="模糊搜索" clearable style="width:180px" @keyup.enter="onSearch" />
          </el-form-item>
          <el-form-item v-for="c in searchCols" :key="c.prop" :label="c.label">
            <el-select v-if="c.type==='select'" v-model="filters[c.prop]" clearable :placeholder="'请选择'+c.label" style="width:160px">
              <el-option v-for="o in optionsFor(c)" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-input v-else v-model="filters[c.prop]" :placeholder="'请输入'+c.label" clearable style="width:180px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
            <el-button type="success" @click="openCreate">新增</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="list" v-loading="loading" border stripe style="width:100%">
          <el-table-column type="index" label="#" width="50" />
          <template v-for="c in tableCols" :key="c.prop">
            <el-table-column :prop="c.prop" :label="c.label" :width="c.width" :min-width="c.minWidth">
              <template #default="{ row }" v-if="c.type==='switch'">
                <el-tag :type="row[c.prop]==1 ? 'success' : 'info'">
                  {{ row[c.prop]==1 ? (c.activeText||'是') : (c.inactiveText||'否') }}
                </el-tag>
              </template>
              <template #default="{ row }" v-else-if="(c.dictCode || c.options || c.type==='remote-select')">
                {{ labelOf(c, row[c.prop]) }}
              </template>
            </el-table-column>
          </template>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
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
                :placeholder="c.placeholder || ('请输入关键词搜索'+c.label)"
                style="width:100%">
                <el-option v-for="o in (remoteOptions[c.prop] || [])" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-switch v-else-if="c.type==='switch'" v-model="form[c.prop]" :active-value="1" :inactive-value="0" />
              <el-input v-else-if="c.type==='textarea'" v-model="form[c.prop]" type="textarea" :rows="3" :placeholder="'请输入'+c.label" />
              <el-date-picker v-else-if="c.type==='date'" v-model="form[c.prop]" type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择时间" style="width:100%" />
              <el-input-number v-else-if="c.type==='number'" v-model="form[c.prop]" :min="0" controls-position="right" style="width:100%" />
              <el-input v-else v-model="form[c.prop]" :placeholder="'请输入'+c.label" :disabled="c.disabled" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible=false">取消</el-button>
            <el-button type="primary" @click="submit">确定</el-button>
          </template>
        </el-dialog>
      </div>`
    };
  };
})();
