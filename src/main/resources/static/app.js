/* 部落冲突部落联赛管理系统 — 前端主程序（Vue3 + Element Plus，零构建） */
(function () {
  const { createApp, ref, reactive, computed, onMounted, nextTick } = Vue;
  const { createRouter, createWebHashHistory } = VueRouter;

  // 从全局加载列配置和 CRUD 组件实例（由 cols.js / crud-instances.js 暴露）
  const {
    clanCrud, memberCrud,
    warCrud, warRecordCrud,
    leagueCrud, leagueRecordCrud, leagueSignupCrud,
    groupCrud, menuCrud,
    dictGroupCrud, dictItemCrud
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
          this.$router.replace('/dashboard');
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
       */
      visibleNav() {
        const all = COC.store.menus || [];
        return all
          .filter((m) => (m.parentId == null || m.parentId === 0) && m.path)
          .map((m) => ({
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
              <el-button size="small">{{ user.superAdmin ? '超级管理员' : '用户' }}<el-icon><ArrowDown /></el-icon></el-button>
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
         *  子菜单在父菜单的 children 字段里，不是平铺在顶层数组。 */
        subMenus() {
          const all = COC.store.menus || [];
          // 找到当前顶级菜单（按 path 匹配）
          const parent = all.find((m) => m.path === parentPath);
          if (!parent) return [];
          // 子菜单在父菜单的 children 中
          return (parent.children || []).filter((m) => m.path);
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
    '/league/record': { name: 'rec',  label: '联赛战绩', component: 'leagueRecordCrud' },
    '/league/signup': { name: 'sign', label: '联赛报名', component: 'leagueSignupCrud' }
  }, {
    components: { leagueCrud, leagueRecordCrud, leagueSignupCrud }
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
        this.form = { username: '', nickname: '', phone: '', email: '', status: 1, password: '' };
        this.dialogVisible = true;
      },
      openEdit(row) {
        this.dialogTitle = '编辑用户';
        this.form = Object.assign({}, row, { password: '' });
        this.dialogVisible = true;
      },
      async submit() {
        const payload = Object.assign({}, this.form);
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
          <el-button type="success" @click="openCreate">新增</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="row.status==1?'success':'info'">{{ row.status==1?'启用':'禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="openRole(row)">分配角色</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
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
          <el-form-item label="用户名"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
          <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item :label="form.id?'重置密码':'密码'">
            <el-input v-model="form.password" type="password" show-password :placeholder="form.id?'留空则不修改':'请输入密码'" />
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
          // 等待 tree 渲染完成再回显勾选
          this.$nextTick(() => {
            this.menuCheckedIds = Array.isArray(ids) ? ids.map(Number) : [];
          });
        } catch (e) {
          this.menuDialog = false;
        }
      },
      /** 树节点勾选变化（保留响应式，但保存时不再依赖此值） */
      handleMenuCheck() {
        // el-tree 内部状态已更新，保存时通过 getCheckedNodes / getHalfCheckedNodes 读取
      },
      async saveAssignMenu() {
        if (!this.menuRoleId) return;
        this.savingMenu = true;
        try {
          // 通过 el-tree API 直接读取选中状态，避免自己维护 menuCheckedIds 的同步问题
          const treeRef = this.$refs.menuTreeRef;
          if (!treeRef) {
            ElementPlus.ElMessage.error('菜单树未初始化');
            return;
          }
          // 勾选节点：checked=true 的节点（含父节点）
          const checked = treeRef.getCheckedNodes(false, false) || [];
          // 半选节点：父节点因部分子节点被选中而处于半选状态
          const halfChecked = treeRef.getHalfCheckedNodes() || [];
          // 收集所有勾选 + 半选的节点 id（去重）
          // - 叶子节点：勾选时收集
          // - 父节点：勾选或半选时收集（半选保证父菜单可见，子菜单按各自勾选状态分配）
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
          <el-button type="success" @click="openCreate">新增</el-button>
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
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="openAssignMenu(row)">分配菜单</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
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
          title="父节点勾选时会自动展开为该节点下全部叶子；取消父节点勾选不影响其下已勾选的叶子。" />
        <el-tree ref="menuTreeRef"
          :data="menuTreeData"
          :props="menuTreeProps"
          show-checkbox
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

  /* ============ 字典管理 ============ */
  const DictManage = {
    components: { dictGroupCrud, dictItemCrud },
    template: `
    <el-tabs class="coc-tabs" v-model="t">
      <el-tab-pane label="字典分组" name="g"><component :is="'dictGroupCrud'" /></el-tab-pane>
      <el-tab-pane label="字典项" name="i"><component :is="'dictItemCrud'" /></el-tab-pane>
    </el-tabs>`,
    data() { return { t: 'g' }; }
  };

  // SystemPage 必须放在 UserManage / RoleManage / DictManage 之后定义，
  // 否则 const 的 TDZ（Temporal Dead Zone）会触发 "Cannot access 'X' before initialization"
  const SystemPage = makeSubTabsMixin('/system', {
    '/clan/group': { name: 'g', label: '部落群组', component: 'groupCrud' },
    '/sys/user':   { name: 'u', label: '用户管理', component: 'UserManage' },
    '/sys/role':   { name: 'r', label: '角色管理', component: 'RoleManage' },
    '/sys/menu':   { name: 'm', label: '菜单管理', component: 'menuCrud' },
    '/dict':       { name: 'd', label: '字典管理', component: 'DictManage' }
  }, {
    components: { groupCrud, UserManage, RoleManage, menuCrud, DictManage }
  });

  /* ============ 路由 ============ */
  const routes = [
    { path: '/login', component: Login, meta: { public: true } },
    {
      path: '/', component: Layout, children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: Dashboard, meta: { title: '数据看板' } },
        { path: 'clan', component: ClanPage },
        { path: 'war', component: WarPage },
        { path: 'league', component: LeaguePage },
        { path: 'system', component: SystemPage, meta: { perm: 'system:manage' } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
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
  app.use(ElementPlus);
  for (const [key, comp] of Object.entries(ElementPlusIconsVue)) app.component(key, comp);
  app.mount('#app');
})();
