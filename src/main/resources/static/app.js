/* 部落冲突部落联赛管理系统 — 前端主程序（Vue3 + Element Plus，零构建） */
(function () {
  const { createApp, ref, reactive, computed, onMounted, nextTick } = Vue;
  const { createRouter, createWebHashHistory } = VueRouter;

  // 从全局加载列配置和 CRUD 组件实例（由 cols.js / crud-instances.js 暴露）
  const {
    clanCrud, memberCrud,
    warCrud, warRecordCrud,
    leagueCrud, leagueClanScoreCrud, leagueRecordCrud, leagueSignupCrud,
    groupCrud, menuTree,
    dictGroupCrud, dictItemCrud,
    configCrud
  } = window.COC_CRUD;

  /* ============ 登录页 ============ */
  const Login = {
    data() {
      return {
        mode: 'login',
        loginForm: { username: '', password: '' },
        regForm: { username: '', password: '', nickname: '' },
        loading: false
      };
    },
    methods: {
      async doLogin() {
        if (!this.loginForm.username || !this.loginForm.password) {
          ElementPlus.ElMessage.warning('请输入用户名和密码');
          return;
        }
        this.loading = true;
        try {
          const res = await COC.api.login(this.loginForm.username, this.loginForm.password);
          localStorage.setItem('coc_token', res.token);
          COC.store.token = res.token;
          const info = await COC.api.info();
          COC.store.user = (info && info.user) || res.user;
          COC.store.menus = (info && info.menus) || [];
          await this.loadGlobals();
          const redirect = this.$route.query.redirect;
          if (redirect) {
            // 来自公开页（如联赛快速报名）的登录回跳
            window.location.href = redirect;
          } else {
            this.$router.replace('/dashboard');
          }
        } catch (e) { /* 拦截器已提示 */ }
        finally { this.loading = false; }
      },
      async doRegister() {
        if (!this.regForm.username || !this.regForm.password) {
          ElementPlus.ElMessage.warning('请输入用户名和密码');
          return;
        }
        this.loading = true;
        try {
          await COC.api.register(this.regForm);
          ElementPlus.ElMessage.success('注册成功，请登录');
          this.mode = 'login';
          this.loginForm.username = this.regForm.username;
        } catch (e) { /* 提示 */ }
        finally { this.loading = false; }
      },
      async loadGlobals() {
        try {
          const g = await COC.api.page('/api/dict/group', { size: 9999 });
          COC.dictGroups = (g.records || []).map((x) => ({ label: x.groupName + '(' + x.groupCode + ')', value: x.groupCode }));
        } catch (e) {}
        try {
          const r = await COC.api.page('/api/sys/role', { size: 9999 });
          COC.roles = (r.records || []).map((x) => ({ label: x.roleName, value: x.id }));
        } catch (e) {}
      }
    },
    template: `
    <div class="login-wrap">
      <div class="login-card">
        <h2 class="login-title">部落冲突部落联赛管理</h2>
        <p class="login-sub">{{ mode==='login' ? '账号登录' : '注册新账号' }}</p>
        <el-form v-if="mode==='login'" label-width="0" @submit.prevent="doLogin">
          <el-form-item>
            <el-input v-model="loginForm.username" placeholder="用户名" size="large">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-input v-model="loginForm.password" type="password" placeholder="密码" size="large" show-password @keyup.enter="doLogin">
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doLogin">登 录</el-button>
          <div style="margin-top:12px;text-align:right"><el-link type="primary" @click="mode='register'">注册新账号</el-link></div>
        </el-form>
        <el-form v-else label-width="0" @submit.prevent="doRegister">
          <el-form-item><el-input v-model="regForm.username" placeholder="用户名" size="large" /></el-form-item>
          <el-form-item><el-input v-model="regForm.password" type="password" placeholder="密码" show-password size="large" /></el-form-item>
          <el-form-item><el-input v-model="regForm.nickname" placeholder="昵称（可选）" size="large" /></el-form-item>
          <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doRegister">注 册</el-button>
          <div style="margin-top:12px;text-align:right"><el-link type="primary" @click="mode='login'">返回登录</el-link></div>
        </el-form>
      </div>
    </div>`
  };

  /* ============ 布局 ============ */
  /* ============ 布局 ============ */
  // 导航图标默认映射（数据库中 menu.icon 为空时使用 Element Plus 内置图标名）
  const ICON_FALLBACK = {
    '/dashboard': 'Odometer',
    '/clan': 'OfficeBuilding',
    '/war': 'DataAnalysis',
    '/league': 'Trophy',
    '/system': 'Setting'
  };

  /**
   * 菜单比较器：按 sort 字段升序排序，sort 为 null 视为最大排最后，
   * sort 相同时按 id 升序兜底。与后端 AuthService.sortMenuTree 行为一致。
   * @param {{sort?: number, id?: number}} a
   * @param {{sort?: number, id?: number}} b
   */
  function menuSortCompare(a, b) {
    const sa = a.sort;
    const sb = b.sort;
    if (sa == null && sb == null) return (a.id || 0) - (b.id || 0);
    if (sa == null) return 1;
    if (sb == null) return -1;
    if (sa !== sb) return sa - sb;
    return (a.id || 0) - (b.id || 0);
  }

  const Layout = {
    data() {
      return { pwdVisible: false, pwdForm: { oldPassword: '', newPassword: '' } };
    },
    computed: {
      user() { return COC.store.user || {}; },
      /**
       * 左侧导航菜单从 COC.store.menus（后端按当前用户角色过滤）动态计算：
       * - 仅取顶级菜单（parentId 为 0 或 null）
       * - 排除无 path 的条目（按钮/纯权限标识）
       * - icon 优先用数据库中存储的，否则按 path 兜底
       * - 按 sort 字段升序排序（null 视为最大排最后），sort 相同时按 id 兜底
       *   （与后端 buildMenuTree / sortMenuTree 顺序保证一致，此处作为前端兜底）
       */
      visibleNav() {
        const all = COC.store.menus || [];
        const filtered = all.filter((m) => (m.parentId == null || m.parentId === 0) && m.path);
        const sorted = filtered.slice().sort(menuSortCompare);
        return sorted.map((m) => ({
          path: m.path,
          title: m.menuName,
          icon: (m.icon && m.icon.trim()) || ICON_FALLBACK[m.path] || 'Menu',
          perm: m.permission || null
        }));
      }
    },
    async mounted() {
      // 保障首屏：若 menus 为空（页面刷新时 beforeCreate 的 info 请求未完成）补一次
      if (COC.store.token && !(COC.store.menus && COC.store.menus.length)) {
        try {
          const info = await COC.api.info();
          if (info && info.user) COC.store.user = info.user;
          if (info && info.menus) COC.store.menus = info.menus;
        } catch (e) { /* token 失效由拦截器处理 */ }
      }
    },
    methods: {
      active(p) { return this.$route.path === p; },
      async logout() {
        try { await COC.api.logout(); } catch (e) {}
        localStorage.removeItem('coc_token');
        COC.store.token = '';
        COC.store.user = null;
        this.$router.replace('/login');
      },
      async changePwd() {
        if (!this.pwdForm.oldPassword || !this.pwdForm.newPassword) {
          ElementPlus.ElMessage.warning('请输入原密码与新密码');
          return;
        }
        try {
          await COC.api.changePassword(this.pwdForm.oldPassword, this.pwdForm.newPassword);
          ElementPlus.ElMessage.success('密码修改成功，请重新登录');
          this.pwdVisible = false;
          localStorage.removeItem('coc_token');
          COC.store.token = '';
          COC.store.user = null;
          this.$router.replace('/login');
        } catch (e) {}
      }
    },
    template: `
    <div class="layout">
      <aside class="layout-aside">
        <div class="logo">COC 联赛管理</div>
        <nav class="aside-menu">
          <a v-for="n in visibleNav" :key="n.path" :href="'#'+n.path" :class="{active: active(n.path)}">
            <el-icon><component :is="n.icon" /></el-icon>{{ n.title }}
          </a>
        </nav>
      </aside>
      <div class="layout-main">
        <header class="layout-header">
          <span class="title">{{ (visibleNav.find(n=>active(n.path))||{}).title || '部落冲突部落联赛管理系统' }}</span>
          <div class="user">
            <span>欢迎，{{ user.nickname || user.username }}</span>
            <el-dropdown @command="c=>{ if(c==='pwd') pwdVisible=true; if(c==='logout') logout(); }">
              <el-button size="small">用户<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="pwd">修改密码</el-dropdown-item>
                  <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </header>
        <main class="layout-content">
          <div class="page-card"><router-view /></div>
        </main>
      </div>
      <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
        <el-form label-width="90px">
          <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
          <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
        </el-form>
        <template #footer><el-button @click="pwdVisible=false">取消</el-button><el-button type="primary" @click="changePwd">确定</el-button></template>
      </el-dialog>
    </div>`
  };

  /* ============ 看板 ============ */
  const Dashboard = {
    data() {
      return {
        stats: {}, warStat: [], rank: [], loading: true, pie: null, bar: null
      };
    },
    mounted() { this.load(); },
    computed: {
      warWin() { return this.sumBy(1); },
      warLose() { return this.sumBy(0); },
      warUnknown() { return this.sumBy(-1); },
      warTotal() {
        return this.warStat.reduce((s, x) => s + (x.count || 0), 0);
      }
    },
    methods: {
      sumBy(status) {
        const f = this.warStat.find((x) => x.winStatus === status);
        return f ? f.count : 0;
      },
      async load() {
        this.loading = true;
        try {
          const [o, w, r] = await Promise.all([
            COC.api.dashboardOverview(), COC.api.dashboardWarStat(), COC.api.dashboardLeagueRank()
          ]);
          this.stats = o || {};
          this.warStat = w || [];
          this.rank = r || [];
          await nextTick();
          this.renderCharts();
        } finally { this.loading = false; }
      },
      renderCharts() {
        if (this.pie) this.pie.dispose();
        if (this.bar) this.bar.dispose();
        this.pie = echarts.init(this.$refs.pie);
        this.pie.setOption({
          title: { text: '部落战胜负分布', left: 'center' },
          tooltip: { trigger: 'item' },
          legend: { bottom: 0 },
          series: [{
            type: 'pie', radius: '55%',
            data: [
              { name: '胜', value: this.warWin, itemStyle: { color: '#67c23a' } },
              { name: '负', value: this.warLose, itemStyle: { color: '#f56c6c' } },
              { name: '未知', value: this.warUnknown, itemStyle: { color: '#c0c4cc' } }
            ]
          }]
        });
        this.bar = echarts.init(this.$refs.bar);
        this.bar.setOption({
          title: { text: '成员战力排行(Top10)', left: 'center' },
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: this.rank.map((x) => x.memberName), axisLabel: { interval: 0, rotate: 20 } },
          yAxis: { type: 'value' },
          series: [{ type: 'bar', data: this.rank.map((x) => x.score), itemStyle: { color: '#409eff' } }]
        });
      }
    },
    template: `
    <div v-loading="loading">
      <div class="dash-cards">
        <div class="dash-card"><div class="k">部落群组</div><div class="v">{{ stats.clanGroupCount || 0 }}</div></div>
        <div class="dash-card green"><div class="k">部落数量</div><div class="v">{{ stats.clanCount || 0 }}</div></div>
        <div class="dash-card"><div class="k">成员数量</div><div class="v">{{ stats.memberCount || 0 }}</div></div>
        <div class="dash-card orange"><div class="k">联赛场次</div><div class="v">{{ stats.leagueCount || 0 }}</div></div>
        <div class="dash-card red"><div class="k">部落战次数</div><div class="v">{{ stats.warCount || 0 }}</div></div>
      </div>
      <div class="dash-cards">
        <div class="dash-card green"><div class="k">部落战胜场</div><div class="v">{{ warWin }}</div></div>
        <div class="dash-card red"><div class="k">部落战负场</div><div class="v">{{ warLose }}</div></div>
        <div class="dash-card"><div class="k">部落战总数</div><div class="v">{{ warTotal }}</div></div>
      </div>
      <div class="chart-row">
        <div class="chart-box"><div ref="pie" class="chart"></div></div>
        <div class="chart-box"><div ref="bar" class="chart"></div></div>
      </div>
      <el-card class="chart-box" style="margin-top:16px">
        <template #header>联赛成员战力排行</template>
        <el-table :data="rank" border stripe>
          <el-table-column type="index" label="排名" width="70" />
          <el-table-column prop="memberName" label="成员名称" />
          <el-table-column prop="winStars" label="胜利之星" width="110" />
          <el-table-column prop="actualAttacks" label="实际攻击" width="110" />
          <el-table-column prop="destroyRate" label="摧毁率(%)" width="120" />
          <el-table-column prop="score" label="综合战力" width="110" />
        </el-table>
      </el-card>
    </div>`
  };

  /* ============ 业务页（标签页组合 CRUD） ============ */
  /**
   * 通用 mixin：根据当前路由 path 找到对应的顶级菜单，
   * 再从 COC.store.menus 中取出该菜单的子菜单列表，
   * 让二级 tab 受角色菜单表管控。
   * 用法：每个 Page 组件传 parentPath（即顶级菜单的 path）。
   * paneMap 提供每个二级菜单 path → { name, label, component } 的映射。
   * extraOptions 可附加 components 注册（SystemPage 用）。
   */
  function makeSubTabsMixin(parentPath, paneMap, extraOptions) {
    const base = {
      data() {
        return { t: '' };
      },
      computed: {
        /** 当前父菜单的子菜单列表（受角色菜单绑定管控）。
         *  注意：info.menus 是嵌套的树形结构（buildMenuTree 构建的），
         *  子菜单在父菜单的 children 字段里，不是平铺在顶层数组。
         *  按 sort 字段升序排序（null 视为最大排最后），与后端排序一致。 */
        subMenus() {
          const all = COC.store.menus || [];
          // 找到当前顶级菜单（按 path 匹配）
          const parent = all.find((m) => m.path === parentPath);
          if (!parent) return [];
          // 子菜单在父菜单的 children 中，按 sort 排序
          return (parent.children || [])
            .filter((m) => m.path)
            .slice()
            .sort(menuSortCompare);
        },
        /** 根据子菜单 + paneMap 生成实际渲染的 tab 列表 */
        visiblePanes() {
          return this.subMenus
            .map((m) => {
              // 优先用 menu.path 在 paneMap 中查找对应 tab 配置
              const pane = paneMap[m.path];
              if (!pane) return null;
              return {
                name: pane.name,
                label: m.menuName || pane.label,
                component: pane.component
              };
            })
            .filter(Boolean);
        }
      },
      watch: {
        visiblePanes: {
          handler(panes) {
            // 当前激活的 tab 不在可见列表中时，自动切到第一个
            if (panes.length && !panes.some((p) => p.name === this.t)) {
              this.t = panes[0].name;
            }
          },
          immediate: true
        }
      },
      template: `
      <el-tabs class="coc-tabs" v-model="t">
        <el-tab-pane v-for="p in visiblePanes" :key="p.name" :label="p.label" :name="p.name">
          <component :is="p.component" />
        </el-tab-pane>
      </el-tabs>`
    };
    return Object.assign({}, base, extraOptions || {});
  }

  // 配置每个父菜单的二级 tab：path → { name, label, component }
  // name 必须与原 v-model="t" 的初值一致，保持 URL 不变
  const ClanPage = makeSubTabsMixin('/clan', {
    '/clan/crud':   { name: 'clan',   label: '部落管理', component: 'clanCrud' },
    '/clan/member': { name: 'member', label: '部落成员', component: 'memberCrud' }
  }, {
    components: { clanCrud, memberCrud }
  });

  const WarPage = makeSubTabsMixin('/war', {
    '/war/crud':   { name: 'war', label: '部落战',     component: 'warCrud' },
    '/war/record': { name: 'rec', label: '部落战战绩', component: 'warRecordCrud' }
  }, {
    components: { warCrud, warRecordCrud }
  });

  const LeaguePage = makeSubTabsMixin('/league', {
    '/league/crud':   { name: 'lg',   label: '联赛管理', component: 'leagueCrud' },
    '/league/score':  { name: 'sco',  label: '部落成绩', component: 'leagueClanScoreCrud' },
    '/league/record': { name: 'rec',  label: '联赛战绩', component: 'leagueRecordCrud' },
    '/league/signup': { name: 'sign', label: '联赛报名', component: 'leagueSignupCrud' }
  }, {
    components: { leagueCrud, leagueClanScoreCrud, leagueRecordCrud, leagueSignupCrud }
  });

  /* ============ 用户管理（含角色分配） ============ */
  const UserManage = {
    data() {
      return {
        list: [], total: 0, page: 1, size: 10, filters: {}, keyword: '',
        loading: false, dialogVisible: false, dialogTitle: '新增用户', form: {},
        roleDialog: false, roleUserId: null, roleSel: [], savingRole: false,
        roleOptions: [] // 角色下拉选项，组件本地维护避免 COC.roles 异步加载竞态
      };
    },
    async mounted() { await this.load(); },
    methods: {
      async ensureRoleOptions() {
        // 优先用 COC.roles（登录/启动时已加载），空则主动拉一次
        if (COC.roles && COC.roles.length) {
          this.roleOptions = COC.roles;
          return;
        }
        try {
          const r = await COC.api.page('/api/sys/role', { size: 9999 });
          this.roleOptions = (r.records || []).map((x) => ({ label: x.roleName, value: x.id }));
          // 同步到全局供其他组件复用
          COC.roles = this.roleOptions;
        } catch (e) { this.roleOptions = []; }
      },
      async load() {
        this.loading = true;
        try {
          const p = { keyword: this.keyword, current: this.page, size: this.size };
          Object.keys(this.filters).forEach((k) => { if (this.filters[k] !== '') p[k] = this.filters[k]; });
          const r = await COC.api.page('/api/sys/user', p);
          this.list = r.records || [];
          this.total = r.total || 0;
        } finally { this.loading = false; }
      },
      resetSearch() { this.keyword = ''; this.filters = {}; this.page = 1; this.load(); },
      openCreate() {
        this.dialogTitle = '新增用户';
        this.form = { username: '', nickname: '', phone: '', email: '', status: 1, password: '123456' };
        this.dialogVisible = true;
      },
      openEdit(row) {
        this.dialogTitle = '编辑用户';
        this.form = Object.assign({}, row, { password: '' });
        this.dialogVisible = true;
      },
      async submit() {
        const payload = Object.assign({}, this.form);
        if (!payload.username) {
          ElementPlus.ElMessage.warning('用户名不能为空');
          return;
        }
        if (!payload.password) delete payload.password;
        try {
          if (payload.id) await COC.api.update('/api/sys/user', payload);
          else await COC.api.create('/api/sys/user', payload);
          ElementPlus.ElMessage.success('保存成功');
          this.dialogVisible = false;
          this.load();
        } catch (e) {}
      },
      remove(row) {
        ElementPlus.ElMessageBox.confirm('确定删除该用户？', '提示', { type: 'warning' }).then(async () => {
          await COC.api.remove('/api/sys/user', row.id);
          ElementPlus.ElMessage.success('删除成功');
          this.load();
        }).catch(() => {});
      },
      async openRole(row) {
        this.roleUserId = row.id;
        this.roleSel = (row.roleIds || []).map(Number);
        await this.ensureRoleOptions();
        this.roleDialog = true;
      },
      async saveRole() {
        this.savingRole = true;
        try {
          await COC.api.assignRole(this.roleUserId, this.roleSel);
          ElementPlus.ElMessage.success('角色分配成功');
          this.roleDialog = false;
          this.load();
        } finally { this.savingRole = false; }
      }
    },
    template: `
    <div>
      <el-form inline @submit.prevent class="coc-toolbar">
        <el-form-item label="关键字"><el-input v-model="keyword" placeholder="用户名/昵称" clearable style="width:180px" @keyup.enter="load" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="状态" style="width:140px">
            <el-option label="启用" :value="1" /><el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button v-if="COC.store.hasPerm('sys:user:add')" type="success" @click="openCreate">新增</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="groupNo" label="群组编号" width="120" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="row.status==1?'success':'info'">{{ row.status==1?'启用':'禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="COC.store.hasPerm('sys:user:edit')" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="COC.store.hasPerm('sys:user:edit')" link type="warning" @click="openRole(row)">分配角色</el-button>
            <el-button v-if="COC.store.hasPerm('sys:user:delete')" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:12px">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper" background
          @current-change="load" @size-change="load" />
      </div>
      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
        <el-form :model="form" label-width="90px">
          <el-form-item label="用户名" required><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
          <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item :label="form.id?'重置密码':'密码'">
            <el-input v-model="form.password" type="password" show-password :placeholder="form.id?'留空则不修改':'默认123456'" />
          </el-form-item>
        </el-form>
        <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="submit">确定</el-button></template>
      </el-dialog>
      <el-dialog v-model="roleDialog" title="分配角色" width="480px">
        <el-select v-model="roleSel" multiple placeholder="请选择角色" style="width:100%">
          <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
        </el-select>
        <template #footer><el-button @click="roleDialog=false">取消</el-button><el-button type="primary" :loading="savingRole" @click="saveRole">保存</el-button></template>
      </el-dialog>
    </div>`
  };

  /* ============ 角色管理（含菜单分配） ============ */
  const RoleManage = {
    data() {
      return {
        list: [], total: 0, page: 1, size: 10, filters: {}, keyword: '',
        loading: false, dialogVisible: false, dialogTitle: '新增角色', form: {},
        menuDialog: false, menuRoleId: null, menuRoleName: '',
        menuTreeData: [], menuCheckedIds: [], menuTreeProps: { children: 'children', label: 'menuName' },
        savingMenu: false, menuTreeRef: null
      };
    },
    async mounted() { await this.load(); },
    methods: {
      async load() {
        this.loading = true;
        try {
          const p = { keyword: this.keyword, current: this.page, size: this.size };
          Object.keys(this.filters).forEach((k) => { if (this.filters[k] !== '') p[k] = this.filters[k]; });
          const r = await COC.api.page('/api/sys/role', p);
          this.list = r.records || [];
          this.total = r.total || 0;
        } finally { this.loading = false; }
      },
      resetSearch() { this.keyword = ''; this.filters = {}; this.page = 1; this.load(); },
      openCreate() {
        this.dialogTitle = '新增角色';
        this.form = { roleCode: '', roleName: '', status: 1, remark: '' };
        this.dialogVisible = true;
      },
      openEdit(row) {
        this.dialogTitle = '编辑角色';
        this.form = Object.assign({}, row);
        this.dialogVisible = true;
      },
      async submit() {
        try {
          if (this.form.id) await COC.api.update('/api/sys/role', this.form);
          else await COC.api.create('/api/sys/role', this.form);
          ElementPlus.ElMessage.success('保存成功');
          this.dialogVisible = false;
          this.load();
        } catch (e) {}
      },
      remove(row) {
        ElementPlus.ElMessageBox.confirm('确定删除该角色？', '提示', { type: 'warning' }).then(async () => {
          await COC.api.remove('/api/sys/role', row.id);
          ElementPlus.ElMessage.success('删除成功');
          this.load();
        }).catch(() => {});
      },
      /** 打开分配菜单弹窗：拉取菜单树 + 当前角色已勾选的菜单 id */
      async openAssignMenu(row) {
        this.menuRoleId = row.id;
        this.menuRoleName = row.roleName || row.roleCode;
        this.menuDialog = true;
        this.menuCheckedIds = [];
        try {
          const tree = await COC.api.menuTree();
          this.menuTreeData = Array.isArray(tree) ? tree : [];
          const ids = await COC.api.roleMenus(row.id);
          // 严格勾选模式下父子互不影响，直接回显所有已绑定节点（含菜单与按钮），
          // 父菜单不会被自动级联取消，确保“取消某菜单下全部按钮仍保留该菜单权限”。
          this.$nextTick(() => {
            this.menuCheckedIds = Array.isArray(ids) ? ids.map(Number) : [];
          });
        } catch (e) {
          this.menuDialog = false;
        }
      },
      /** 树节点勾选变化：自定义级联规则（配合 check-strictly）
       *  - 勾选：向下级联所有子孙 + 向上级联所有祖先（保证菜单页与其按钮同步勾选、菜单在导航可见）
       *  - 取消：仅向下级联子孙，不影响祖先（即取消一个菜单下的全部按钮，仍保留该菜单页权限）
       */
      handleMenuCheck(data, info) {
        if (this._menuCascading) return;
        const treeRef = this.$refs.menuTreeRef;
        if (!treeRef) return;
        const node = treeRef.getNode(data);
        if (!node) return;
        const val = info.checked;
        this._menuCascading = true;
        try {
          // 向下级联：设置当前节点下所有子孙（菜单/按钮）
          const walkDown = (n, v) => {
            if (n.childNodes && n.childNodes.length) {
              n.childNodes.forEach((c) => {
                treeRef.setChecked(c.data.id, v);
                walkDown(c, v);
              });
            }
          };
          walkDown(node, val);
          // 向上级联：仅“勾选”时同步勾选所有祖先，保证菜单页可见
          if (val) {
            let p = node.parent;
            while (p && p.data && p.data.id != null) {
              treeRef.setChecked(p.data.id, true);
              p = p.parent;
            }
          }
        } finally {
          this.$nextTick(() => { this._menuCascading = false; });
        }
      },
      async saveAssignMenu() {
        if (!this.menuRoleId) return;
        this.savingMenu = true;
        try {
          const treeRef = this.$refs.menuTreeRef;
          if (!treeRef) {
            ElementPlus.ElMessage.error('菜单树未初始化');
            return;
          }
          // 收集勾选节点 + 半选节点（父目录半选时可能携带权限标识，如 system:manage，必须保存）
          const checked = treeRef.getCheckedNodes(false, false) || [];
          const halfChecked = treeRef.getHalfCheckedNodes() || [];
          const allNodes = checked.concat(halfChecked);
          const menuIds = [];
          for (const n of allNodes) {
            if (n && n.id != null) {
              const id = Number(n.id);
              if (menuIds.indexOf(id) === -1) menuIds.push(id);
            }
          }
          await COC.api.assignMenus(this.menuRoleId, menuIds);
          ElementPlus.ElMessage.success('菜单分配成功');
          this.menuDialog = false;
        } finally { this.savingMenu = false; }
      },
    },
    template: `
    <div>
      <el-form inline @submit.prevent class="coc-toolbar">
        <el-form-item label="关键字"><el-input v-model="keyword" placeholder="角色编码/名称" clearable style="width:180px" @keyup.enter="load" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button v-if="COC.store.hasPerm('sys:role:add')" type="success" @click="openCreate">新增</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="roleCode" label="角色编码" />
        <el-table-column prop="roleName" label="角色名称" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="row.status==1?'success':'info'">{{ row.status==1?'启用':'禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="COC.store.hasPerm('sys:role:edit')" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="COC.store.hasPerm('sys:role:assign')" link type="warning" @click="openAssignMenu(row)">分配菜单</el-button>
            <el-button v-if="COC.store.hasPerm('sys:role:delete')" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:12px">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper" background
          @current-change="load" @size-change="load" />
      </div>
      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
        <el-form :model="form" label-width="90px">
          <el-form-item label="角色编码"><el-input v-model="form.roleCode" :disabled="!!form.id" /></el-form-item>
          <el-form-item label="角色名称"><el-input v-model="form.roleName" /></el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="submit">确定</el-button></template>
      </el-dialog>
      <el-dialog v-model="menuDialog" :title="'分配菜单 - ' + menuRoleName" width="560px" destroy-on-close>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:8px"
          title="勾选父节点会同时选中其下全部子项（含按钮）；取消某个按钮不会取消其父菜单（菜单页权限仍保留），仅取消父节点才会移除整组。" />
        <el-tree ref="menuTreeRef"
          :data="menuTreeData"
          :props="menuTreeProps"
          show-checkbox
          check-strictly
          node-key="id"
          :default-checked-keys="menuCheckedIds"
          :default-expand-all="true"
          @check="handleMenuCheck"
          style="max-height:420px;overflow:auto;border:1px solid #ebeef5;border-radius:4px;padding:8px;" />
        <template #footer>
          <el-button @click="menuDialog=false">取消</el-button>
          <el-button type="primary" :loading="savingMenu" @click="saveAssignMenu">保存</el-button>
        </template>
      </el-dialog>
    </div>`
  };

  /* ============ 字典管理（二级树：分组 → 字典项） ============ */
  const DictManage = {
    data() {
      return {
        treeData: [],
        keyword: '',
        loading: false,
        groupDialogVisible: false,
        groupDialogTitle: '',
        groupForm: {},
        itemDialogVisible: false,
        itemDialogTitle: '',
        itemForm: {},
        treeProps: {
          children: 'children',
          label: function (data) {
            return data.nodeType === 'group' ? (data.groupName || '') : (data.itemName || '');
          }
        }
      };
    },
    computed: {
      filteredTree() {
        if (!this.keyword || !this.keyword.trim()) return this.treeData;
        var kw = this.keyword.trim().toLowerCase();
        var walk = function (nodes) {
          var out = [];
          for (var i = 0; i < nodes.length; i++) {
            var n = nodes[i];
            var children = Array.isArray(n.children) ? walk(n.children) : [];
            var match = false;
            if (n.nodeType === 'group') {
              match = (n.groupCode || '').toLowerCase().indexOf(kw) !== -1 ||
                      (n.groupName || '').toLowerCase().indexOf(kw) !== -1;
            } else {
              match = (n.itemName || '').toLowerCase().indexOf(kw) !== -1 ||
                      (n.itemValue || '').toLowerCase().indexOf(kw) !== -1;
            }
            if (match || children.length > 0) {
              out.push(Object.assign({}, n, { children: children }));
            }
          }
          return out;
        };
        return walk(this.treeData);
      }
    },
    async mounted() {
      await this.loadTree();
    },
    methods: {
      async loadTree() {
        this.loading = true;
        try {
          var results = await Promise.all([
            COC.api.page('/api/dict/group', { size: 9999 }),
            COC.api.page('/api/dict/item', { size: 9999 })
          ]);
          var groups = results[0].records || [];
          var items = results[1].records || [];
          // 同步全局 COC.dictGroups（供其他模块下拉使用）
          COC.dictGroups = groups.map(function (x) {
            return { label: x.groupName + '(' + x.groupCode + ')', value: x.groupCode };
          });
          // 按 groupCode 分组字典项，并按 sort 升序排列
          var itemMap = {};
          items.forEach(function (it) {
            var key = it.groupCode;
            if (!itemMap[key]) itemMap[key] = [];
            itemMap[key].push(it);
          });
          Object.keys(itemMap).forEach(function (key) {
            itemMap[key].sort(function (a, b) { return (a.sort || 0) - (b.sort || 0); });
          });
          // 组装二级树：分组（一级） → 字典项（二级）
          this.treeData = groups.map(function (g) {
            var children = (itemMap[g.groupCode] || []).map(function (it) {
              return {
                id: 'item_' + it.id,
                realId: it.id,
                nodeType: 'item',
                groupCode: g.groupCode,
                itemName: it.itemName,
                itemValue: it.itemValue,
                sort: it.sort,
                status: it.status
              };
            });
            return {
              id: 'group_' + g.id,
              realId: g.id,
              nodeType: 'group',
              groupCode: g.groupCode,
              groupName: g.groupName,
              status: g.status,
              remark: g.remark,
              children: children
            };
          });
        } catch (e) {
          if (window.ElementPlus && ElementPlus.ElMessage) {
            ElementPlus.ElMessage.error('加载字典失败：' + ((e && e.message) || ''));
          }
        } finally {
          this.loading = false;
        }
      },
      openGroupCreate() {
        this.groupDialogTitle = '新增字典分组';
        this.groupForm = { groupCode: '', groupName: '', remark: '', status: 1 };
        this.groupDialogVisible = true;
      },
      openGroupEdit(data) {
        this.groupDialogTitle = '编辑字典分组';
        this.groupForm = {
          id: data.realId,
          groupCode: data.groupCode,
          groupName: data.groupName,
          remark: data.remark,
          status: data.status
        };
        this.groupDialogVisible = true;
      },
      async submitGroup() {
        if (!this.groupForm.groupCode || !this.groupForm.groupName) {
          ElementPlus.ElMessage.warning('分组编码和名称不能为空');
          return;
        }
        try {
          if (this.groupForm.id) await COC.api.update('/api/dict/group', this.groupForm);
          else await COC.api.create('/api/dict/group', this.groupForm);
          ElementPlus.ElMessage.success('保存成功');
          this.groupDialogVisible = false;
          await this.loadTree();
        } catch (e) { /* 拦截器已提示 */ }
      },
      openItemCreate(groupNode) {
        this.itemDialogTitle = '新增字典项';
        this.itemForm = {
          groupCode: groupNode.groupCode,
          itemName: '',
          itemValue: '',
          sort: 0,
          status: 1
        };
        this.itemDialogVisible = true;
      },
      openItemEdit(data) {
        this.itemDialogTitle = '编辑字典项';
        this.itemForm = {
          id: data.realId,
          groupCode: data.groupCode,
          itemName: data.itemName,
          itemValue: data.itemValue,
          sort: data.sort,
          status: data.status
        };
        this.itemDialogVisible = true;
      },
      async submitItem() {
        if (!this.itemForm.itemName || !this.itemForm.itemValue) {
          ElementPlus.ElMessage.warning('字典名称和值不能为空');
          return;
        }
        try {
          if (this.itemForm.id) await COC.api.update('/api/dict/item', this.itemForm);
          else await COC.api.create('/api/dict/item', this.itemForm);
          ElementPlus.ElMessage.success('保存成功');
          this.itemDialogVisible = false;
          await this.loadTree();
        } catch (e) { /* 拦截器已提示 */ }
      },
      onDelete(data) {
        var self = this;
        if (data.nodeType === 'group') {
          var childCount = (data.children || []).length;
          var msg = '确定删除分组「' + data.groupName + '」？';
          if (childCount > 0) {
            msg = '分组「' + data.groupName + '」下有 ' + childCount + ' 个字典项，删除分组后这些字典项不会被自动删除。确定继续？';
          }
          ElementPlus.ElMessageBox.confirm(msg, '提示', { type: 'warning' }).then(async () => {
            await COC.api.remove('/api/dict/group', data.realId);
            ElementPlus.ElMessage.success('删除成功');
            await self.loadTree();
          }).catch(function () {});
        } else {
          ElementPlus.ElMessageBox.confirm('确定删除字典项「' + data.itemName + '」？', '提示', { type: 'warning' }).then(async () => {
            await COC.api.remove('/api/dict/item', data.realId);
            ElementPlus.ElMessage.success('删除成功');
            await self.loadTree();
          }).catch(function () {});
        }
      }
    },
    template: `
    <div class="dict-manage">
      <div class="dict-toolbar">
        <el-input v-model="keyword" placeholder="搜索分组编码/名称或字典项名称/值" clearable style="width:320px" />
        <el-button type="success" @click="openGroupCreate" style="margin-left:12px">新增分组</el-button>
        <el-button @click="loadTree" style="margin-left:8px">刷新</el-button>
        <span class="dict-tip">共 {{ treeData.length }} 个分组，点击分组节点的「新增字典项」可快速添加</span>
      </div>
      <div v-loading="loading" class="dict-tree-body">
        <el-tree :data="filteredTree" :props="treeProps" node-key="id"
          :default-expand-all="!keyword" :expand-on-click-node="false" empty-text="暂无字典数据">
          <template #default="{ data }">
            <span class="dict-tree-node">
              <span class="dict-tree-label" v-if="data.nodeType === 'group'">
                <el-tag size="small" type="primary">分组</el-tag>
                <span class="dict-name">{{ data.groupName }}</span>
                <span class="dict-code">{{ data.groupCode }}</span>
                <el-tag size="small" :type="data.status==1?'success':'info'">{{ data.status==1?'启用':'禁用' }}</el-tag>
              </span>
              <span class="dict-tree-label" v-else>
                <el-tag size="small" type="info">项</el-tag>
                <span class="dict-name">{{ data.itemName }}</span>
                <span class="dict-code">{{ data.itemValue }}</span>
                <span class="dict-sort">排序 {{ data.sort }}</span>
                <el-tag size="small" :type="data.status==1?'success':'info'">{{ data.status==1?'启用':'禁用' }}</el-tag>
              </span>
              <span class="dict-tree-actions">
                <el-button v-if="data.nodeType === 'group'" link size="small" type="success" @click.stop="openItemCreate(data)">新增字典项</el-button>
                <el-button link size="small" type="primary" @click.stop="data.nodeType === 'group' ? openGroupEdit(data) : openItemEdit(data)">编辑</el-button>
                <el-button link size="small" type="danger" @click.stop="onDelete(data)">删除</el-button>
              </span>
            </span>
          </template>
        </el-tree>
      </div>
      <el-dialog v-model="groupDialogVisible" :title="groupDialogTitle" width="480px" destroy-on-close>
        <el-form :model="groupForm" label-width="90px">
          <el-form-item label="分组编码" required><el-input v-model="groupForm.groupCode" :disabled="!!groupForm.id" placeholder="如 league_tier" /></el-form-item>
          <el-form-item label="分组名称" required><el-input v-model="groupForm.groupName" placeholder="如 联赛段位" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="groupForm.remark" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="状态"><el-switch v-model="groupForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="groupDialogVisible=false">取消</el-button><el-button type="primary" @click="submitGroup">确定</el-button></template>
      </el-dialog>
      <el-dialog v-model="itemDialogVisible" :title="itemDialogTitle" width="480px" destroy-on-close>
        <el-form :model="itemForm" label-width="90px">
          <el-form-item label="所属分组">
            <el-input :model-value="itemForm.groupCode" disabled />
          </el-form-item>
          <el-form-item label="字典名称" required><el-input v-model="itemForm.itemName" placeholder="如 铜杯III" /></el-form-item>
          <el-form-item label="字典值" required><el-input v-model="itemForm.itemValue" placeholder="如 bronze_3" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="itemForm.sort" :min="0" controls-position="right" style="width:100%" /></el-form-item>
          <el-form-item label="状态"><el-switch v-model="itemForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="itemDialogVisible=false">取消</el-button><el-button type="primary" @click="submitItem">确定</el-button></template>
      </el-dialog>
    </div>`
  };

  /* ============ 入组申请页面 ============ */
  const ClanGroupApplyPage = {
    data() {
      return {
        loading: false,
        submitting: false,
        records: [],
        total: 0,
        page: 1,
        size: 10,
        query: { applyStatus: '' },
        form: { groupNo: '' },
        groups: [],
        groupLoading: false
      };
    },
    computed: {
      user() { return COC.store.user || {}; },
      roleCodes() { return this.user.roleCodes || []; },
      isSuperAdmin() { return !!this.user.superAdmin; },
    canApprove() {
      return COC.store.hasPerm('group:apply:approve');
    },
      statusOptions() {
        return [{ label: '申请中', value: 1 }, { label: '同意', value: 2 }, { label: '拒绝', value: 3 }];
      }
    },
    async mounted() { await this.load(); },
    methods: {
      statusLabel(status) {
        const s = Number(status);
        if (s === 1) return '申请中';
        if (s === 2) return '同意';
        if (s === 3) return '拒绝';
        return status;
      },
      async load() {
        this.loading = true;
        try {
          const params = { current: this.page, size: this.size };
          if (this.query.applyStatus !== '') params.applyStatus = this.query.applyStatus;
          const r = await COC.api.applyPage(params);
          this.records = r.records || [];
          this.total = r.total || 0;
        } finally { this.loading = false; }
      },
      async searchGroups(keyword) {
        if (!keyword || keyword.length < 1) { this.groups = []; return; }
        this.groupLoading = true;
        try {
          const r = await COC.api.page('/api/clan/group', { keyword, size: 20 });
          this.groups = r.records || [];
        } finally { this.groupLoading = false; }
      },
      async submit() {
        if (!this.form.groupNo) {
          ElementPlus.ElMessage.warning('请选择要申请的群组');
          return;
        }
        this.submitting = true;
        try {
          await COC.api.applyCreate({ groupNo: this.form.groupNo });
          ElementPlus.ElMessage.success('申请已提交');
          this.form.groupNo = '';
          this.page = 1;
          await this.load();
        } finally { this.submitting = false; }
      },
      async approve(row) {
        try {
          await ElementPlus.ElMessageBox.confirm('确认同意该用户的入组申请？', '提示', { type: 'warning' });
          await COC.api.applyApprove(row.id);
          ElementPlus.ElMessage.success('已同意');
          await this.load();
        } catch (e) { /* 取消 */ }
      },
      async reject(row) {
        try {
          await ElementPlus.ElMessageBox.confirm('确认拒绝该用户的入组申请？', '提示', { type: 'warning' });
          await COC.api.applyReject(row.id);
          ElementPlus.ElMessage.success('已拒绝');
          await this.load();
        } catch (e) { /* 取消 */ }
      },
      async cancel(row) {
        try {
          await ElementPlus.ElMessageBox.confirm('确认撤销该申请？', '提示', { type: 'warning' });
          await COC.api.applyDelete(row.id);
          ElementPlus.ElMessage.success('已撤销');
          await this.load();
        } catch (e) { /* 取消 */ }
      }
    },
    template: `
    <div class="page-apply" style="padding:16px">
      <el-card v-if="!canApprove && !isSuperAdmin" style="margin-bottom:16px">
        <template #header><b>提交入组申请</b></template>
        <el-form :model="form" label-width="100px" inline>
          <el-form-item label="目标群组" required>
            <el-select v-model="form.groupNo" filterable remote :remote-method="searchGroups"
              :loading="groupLoading" placeholder="请输入群组名称/编号搜索" style="width:320px">
              <el-option v-for="g in groups" :key="g.groupNo" :label="g.groupName + ' (' + g.groupNo + ')'" :value="g.groupNo" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submit" :loading="submitting">提交申请</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-form v-if="canApprove" inline @submit.prevent class="coc-toolbar">
        <el-form-item label="申请状态">
          <el-select v-model="query.applyStatus" clearable placeholder="全部" style="width:140px" @change="page=1;load()">
            <el-option label="申请中" :value="1" />
            <el-option label="同意" :value="2" />
            <el-option label="拒绝" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="page=1;load()">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="申请人账号" width="130" />
        <el-table-column prop="nickname" label="昵称" width="130" />
        <el-table-column prop="groupName" label="目标群组" min-width="160" />
        <el-table-column prop="applyStatus" label="状态" width="100">
          <template #default="{row}">
            <el-tag v-if="row.applyStatus === 1" type="warning">{{ statusLabel(row.applyStatus) }}</el-tag>
            <el-tag v-else-if="row.applyStatus === 2" type="success">{{ statusLabel(row.applyStatus) }}</el-tag>
            <el-tag v-else-if="row.applyStatus === 3" type="danger">{{ statusLabel(row.applyStatus) }}</el-tag>
            <span v-else>{{ statusLabel(row.applyStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <template v-if="canApprove && row.applyStatus === 1">
              <el-button size="small" type="success" @click="approve(row)">同意</el-button>
              <el-button size="small" type="danger" @click="reject(row)">拒绝</el-button>
            </template>
            <el-button v-if="!canApprove && !isSuperAdmin && row.applyStatus === 1" size="small" type="danger" @click="cancel(row)">撤销</el-button>
            <span v-if="row.applyStatus !== 1">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div style="display:flex;justify-content:flex-end;margin-top:12px">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper" background
          @current-change="load" @size-change="load" />
      </div>
    </div>`
  };

  // SystemPage 必须放在 UserManage / RoleManage / DictManage / ClanGroupApplyPage 之后定义，
  // 否则 const 的 TDZ（Temporal Dead Zone）会触发 "Cannot access 'X' before initialization"
  const GroupMemberPage = {
    template: /*html*/`
      <div>
        <el-form inline @submit.prevent class="coc-toolbar">
          <el-form-item label="关键字"><el-input v-model="keyword" placeholder="用户名/昵称" clearable style="width:180px" @keyup.enter="search" /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="records" v-loading="loading" border stripe>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="nickname" label="昵称" min-width="140" />
          <el-table-column label="角色" width="200">
            <template v-slot="{row}">
              <el-tag v-for="role in (row.roleCodes || [])" :key="role" size="small" style="margin-right:4px"
                :type="role==='SUPER_ADMIN'?'danger':role==='GROUP_ADMIN'?'warning':role==='LEAGUE_ADMIN'?'primary':''">
                {{ roleLabel(role) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template v-slot="{row}">
              <template v-if="canSetOrCancelAdmin">
                <el-popconfirm v-if="hasRole(row, 'LEAGUE_ADMIN')" title="确认取消该成员的部落管理员身份？" @confirm="doCancelAdmin(row)">
                  <template #reference>
                    <el-button type="warning" size="small" plain>取消部落管理员</el-button>
                  </template>
                </el-popconfirm>
                <el-popconfirm v-else-if="!hasRole(row, 'GROUP_ADMIN')" title="确认设为部落管理员？" @confirm="doSetAdmin(row)">
                  <template #reference>
                    <el-button type="primary" size="small" plain style="margin-left:6px">设为部落管理员</el-button>
                  </template>
                </el-popconfirm>
              </template>
              <el-popconfirm v-if="canKick(row)" title="确认要踢出该成员并恢复为游客吗？" @confirm="doKick(row)">
                <template #reference>
                  <el-button type="danger" size="small" plain style="margin-left:6px">踢出</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <div style="display:flex;justify-content:flex-end;margin-top:12px">
          <el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :total="page.total"
            :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper" background
            @current-change="c=>load(page.size,c)" @size-change="s=>load(s,1)" />
        </div>
      </div>`,
    data() {
      return {
        records: [],
        page: { current: 1, size: 10, total: 0 },
        loading: false,
        keyword: ''
      };
    },
    computed: {
      me() { return COC.store.user || {}; },
      myRoleCodes() { return this.me.roleCodes || []; },
      isGroupAdmin() { return this.myRoleCodes.indexOf('GROUP_ADMIN') !== -1; },
      // 是否可设置/取消部落管理员（由菜单按钮权限 group:user:setAdmin / cancelAdmin 控制）
      canSetOrCancelAdmin() {
        return COC.store.hasPerm('group:user:setAdmin') || COC.store.hasPerm('group:user:cancelAdmin');
      }
    },
    mounted() { this.load(); },
    methods: {
      async load(size, current) {
        this.loading = true;
        try {
          var s = size || this.page.size;
          var c = current || 1;
          var res = await COC.api.groupMemberPage({ current: c, size: s, keyword: this.keyword });
          var data = res.data || res;
          this.records = data.records || [];
          this.page = { current: data.current || c, size: data.size || s, total: data.total || 0 };
        } catch(e) {
          ElementPlus.ElMessage.error('加载失败');
        } finally { this.loading = false; }
      },
      search() { this.load(10, 1); },
      resetSearch() { this.keyword = ''; this.load(10, 1); },
      hasRole(row, code) {
        return (row.roleCodes || []).indexOf(code) !== -1;
      },
      onlyMember(row) {
        return this.hasRole(row, 'MEMBER')
          && !this.hasRole(row, 'LEAGUE_ADMIN')
          && !this.hasRole(row, 'GROUP_ADMIN')
          && !this.hasRole(row, 'SUPER_ADMIN')
          && !this.hasRole(row, 'VISITOR');
      },
      canKick(row) {
        return COC.store.hasPerm('group:user:kick') && this.onlyMember(row);
      },
      roleLabel(code) {
        var m = {SUPER_ADMIN:'超级管理员',GROUP_ADMIN:'群主',LEAGUE_ADMIN:'部落管理员',MEMBER:'成员',VISITOR:'游客'};
        return m[code] || code;
      },
      async doSetAdmin(row) {
        try {
          await COC.api.groupMemberSetAdmin(row.id);
          ElementPlus.ElMessage.success('已设为部落管理员');
          this.load(this.page.size, this.page.current);
        } catch(e) { ElementPlus.ElMessage.error('操作失败'); }
      },
      async doKick(row) {
        try {
          await COC.api.groupMemberKick(row.id);
          ElementPlus.ElMessage.success('已踢出成员');
          this.load(this.page.size, this.page.current);
        } catch(e) { ElementPlus.ElMessage.error('操作失败'); }
      },
      async doCancelAdmin(row) {
        try {
          await COC.api.groupMemberCancelAdmin(row.id);
          ElementPlus.ElMessage.success('已取消部落管理员');
          this.load(this.page.size, this.page.current);
        } catch(e) { ElementPlus.ElMessage.error('操作失败'); }
      }
    }
  };
  const SystemPage = makeSubTabsMixin('/system', {
    '/clan/group': { name: 'g', label: '部落群组', component: 'groupCrud' },
    '/clan/group/user': { name: 'gu', label: '群组成员', component: 'GroupMemberPage' },
    '/clan/group/apply': { name: 'a', label: '入组申请', component: 'ClanGroupApplyPage' },
    '/sys/user':   { name: 'u', label: '用户管理', component: 'UserManage' },
    '/sys/role':   { name: 'r', label: '角色管理', component: 'RoleManage' },
    '/sys/menu':   { name: 'm', label: '菜单管理', component: 'menuTree' },
    '/sys/config': { name: 'c', label: '系统配置', component: 'configCrud' },
    '/dict':       { name: 'd', label: '字典管理', component: 'DictManage' }
  }, {
    components: { groupCrud, GroupMemberPage, ClanGroupApplyPage, UserManage, RoleManage, menuTree, DictManage, configCrud }
  });

  /* ============ 404 页面 ============ */
  const NotFound = {
    template: `
    <div class="not-found">
      <el-result icon="warning" title="404" sub-title="页面不存在或无访问权限">
        <template #extra>
          <el-button type="primary" @click="$router.replace('/dashboard')">返回首页</el-button>
        </template>
      </el-result>
    </div>`
  };

  /** 将后端返回的扁平菜单数组展开为 path 集合（用于菜单地址拦截判断） */
  function flattenMenuPaths(menus) {
    const set = new Set();
    (Array.isArray(menus) ? menus : []).forEach((m) => { if (m && m.path) set.add(m.path); });
    return set;
  }

  /* ============ 路由 ============ */
  const routes = [
    { path: '/login', component: Login, meta: { public: true } },
    {
      path: '/', component: Layout, children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: Dashboard, meta: { title: '数据看板' } },
        { path: 'clan', component: ClanPage },
        { path: 'clan/group/apply', component: ClanGroupApplyPage, meta: { title: '入组申请', perm: 'group:apply:list' } },
        { path: 'war', component: WarPage },
        { path: 'league', component: LeaguePage },
        { path: 'system', component: SystemPage }
      ]
    },
    { path: '/404', component: NotFound },
    { path: '/:pathMatch(.*)*', redirect: '/404' }
  ];

  const router = createRouter({ history: createWebHashHistory(), routes });
  router.beforeEach(async (to) => {
    if (!to.meta.public && !COC.store.token) return '/login';
    if (to.path === '/login' && COC.store.token) return '/dashboard';
    // 首次进入或刷新页面：info 接口可能还没完成，先等待加载用户信息/权限/菜单
    if (COC.store.token && !COC.store.user) {
      try {
        const info = await COC.api.info();
        if (info && info.user) COC.store.user = info.user;
        if (info && info.menus) COC.store.menus = info.menus;
      } catch (e) {
        // token 失效或网络错误，留在原页面或去登录
        return '/login';
      }
    }
    if (to.meta.perm && !COC.store.hasPerm(to.meta.perm)) {
      ElementPlus.ElMessage.error('无访问权限');
      return '/dashboard';
    }
    // 菜单地址拦截：导航到后台菜单页面但当前用户无对应菜单数据时，跳转到 404 页面。
    // （/dashboard 为所有登录用户首页，不在此拦截，避免无菜单数据时锁死首页）
    var MENU_ROUTE_MAP = {
      '/clan': '/clan',
      '/clan/group/apply': '/clan/group/apply',
      '/war': '/war',
      '/league': '/league',
      '/system': '/system'
    };
    if (MENU_ROUTE_MAP[to.path] && !flattenMenuPaths(COC.store.menus).has(MENU_ROUTE_MAP[to.path])) {
      return '/404';
    }
  });

  /* ============ 启动 ============ */
  const app = createApp({
    template: `<router-view />`,
    async beforeCreate() {
      if (COC.store.token) {
        try {
          const info = await COC.api.info();
          COC.store.user = info.user || info;
          // 同步填充菜单树，供 Layout 组件渲染左侧导航
          COC.store.menus = (info && info.menus) || [];
          const g = await COC.api.page('/api/dict/group', { size: 9999 });
          COC.dictGroups = (g.records || []).map((x) => ({ label: x.groupName + '(' + x.groupCode + ')', value: x.groupCode }));
          const r = await COC.api.page('/api/sys/role', { size: 9999 });
          COC.roles = (r.records || []).map((x) => ({ label: x.roleName, value: x.id }));
        } catch (e) { /* token 失效 */ }
      }
    }
  });
  app.use(router);
  app.use(ElementPlus, { locale: window.ElementPlusLocaleZhCn });
  // 将全局对象 COC 挂到组件实例上，使模板表达式（如 v-if="COC.store.hasPerm(...)"）
  // 能通过 _ctx.COC 解析到 window.COC，否则模板渲染时 COC 为 undefined 会抛错。
  app.config.globalProperties.COC = window.COC;
  for (const [key, comp] of Object.entries(ElementPlusIconsVue)) app.component(key, comp);
  app.mount('#app');
})();
