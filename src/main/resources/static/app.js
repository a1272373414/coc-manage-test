/* 部落冲突部落联赛管理系统 — 前端主程序（Vue3 + Element Plus，零构建） */
(function () {
  const { createApp, ref, reactive, computed, onMounted, nextTick } = Vue;
  const { createRouter, createWebHashHistory } = VueRouter;
  const req = (msg) => [{ required: true, message: msg, trigger: 'blur' }];

  /* ============ 列配置 ============ */
  const clanCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'clanName', label: '部落名称', search: true, rule: req('请输入部落名称') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'intro', label: '简介', type: 'textarea' }
  ];
  const memberCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'memberName', label: '成员名称', search: true, rule: req('请输入成员名称') },
    { prop: 'memberNo', label: '成员编号', rule: req('请输入成员编号') },
    { prop: 'clanNo', label: '部落编号', search: true },
    { prop: 'warStatus', label: '参战状态', type: 'switch', activeText: '参战', inactiveText: '不参战' },
    { prop: 'intro', label: '简介', type: 'textarea' }
  ];
  const warCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'warNo', label: '部落战编号', search: true, rule: req('请输入部落战编号') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'winStatus', label: '胜负', type: 'switch', activeText: '胜', inactiveText: '负' },
    { prop: 'startTime', label: '开始时间', type: 'date' },
    { prop: 'intro', label: '描述', type: 'textarea' }
  ];
  const warRecordCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'warNo', label: '部落战编号', search: true, rule: req('请输入部落战编号') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'memberName', label: '成员名称', rule: req('请输入成员名称') },
    { prop: 'memberNo', label: '成员编号', rule: req('请输入成员编号') },
    { prop: 'atk1Stars', label: '一攻星数', type: 'number' },
    { prop: 'atk1Rate', label: '一攻百分比', type: 'number' },
    { prop: 'atk2Stars', label: '二攻星数', type: 'number' },
    { prop: 'atk2Rate', label: '二攻百分比', type: 'number' },
    { prop: 'actualAttacks', label: '实际攻击次数', type: 'number' }
  ];
  const leagueCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'leagueName', label: '联赛名称', search: true, rule: req('请输入联赛名称') },
    { prop: 'leagueNo', label: '联赛编号', search: true, rule: req('请输入联赛编号') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'signupStart', label: '报名开始', type: 'date' },
    { prop: 'signupEnd', label: '报名结束', type: 'date' },
    { prop: 'tier', label: '联赛等级', type: 'number' },
    { prop: 'resultRank', label: '本段排名', type: 'number' },
    { prop: 'extraCount', label: '额外人数', type: 'number' },
    { prop: 'leagueCoin', label: '联赛币', type: 'number' },
    { prop: 'extraCoin', label: '额外币', type: 'number' },
    { prop: 'promoteStatus', label: '升降级', type: 'select', default: 0, options: [{ label: '无', value: 0 }, { label: '晋升', value: 1 }, { label: '降级', value: 2 }] },
    { prop: 'intro', label: '简介', type: 'textarea' }
  ];
  const leagueRecordCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'leagueNo', label: '联赛编号', search: true, rule: req('请输入联赛编号') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'memberName', label: '成员名称', rule: req('请输入成员名称') },
    { prop: 'memberNo', label: '成员编号', rule: req('请输入成员编号') },
    { prop: 'winStars', label: '胜利之星', type: 'number' },
    { prop: 'destroyRate', label: '摧毁率(%)', type: 'number' },
    { prop: 'actualAttacks', label: '实际攻击', type: 'number' },
    { prop: 'requiredAttacks', label: '要求攻击', type: 'number' },
    { prop: 'hasExtra', label: '额外参赛', type: 'select', default: 0, options: [{ label: '否', value: 0 }, { label: '是', value: 1 }] }
  ];
  const leagueSignupCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'leagueNo', label: '联赛编号', search: true, rule: req('请输入联赛编号') },
    { prop: 'clanNo', label: '部落编号', search: true, rule: req('请输入部落编号') },
    { prop: 'memberName', label: '成员名称', rule: req('请输入成员名称') },
    { prop: 'memberNo', label: '成员编号', rule: req('请输入成员编号') },
    { prop: 'signupStatus', label: '报名状态', type: 'select', default: 1, options: [{ label: '取消', value: 0 }, { label: '报名', value: 1 }] },
    { prop: 'signupTime', label: '报名时间', type: 'date' }
  ];
  const groupCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'groupNo', label: '群组编号', search: true, rule: req('请输入群组编号') },
    { prop: 'groupName', label: '群组名称', search: true, rule: req('请输入群组名称') },
    { prop: 'ownerId', label: '群主ID', type: 'number' },
    { prop: 'intro', label: '简介', type: 'textarea' },
    { prop: 'status', label: '状态', type: 'switch', activeText: '启用', inactiveText: '禁用' }
  ];
  const roleCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'roleCode', label: '角色编码', search: true, rule: req('请输入角色编码') },
    { prop: 'roleName', label: '角色名称', search: true, rule: req('请输入角色名称') },
    { prop: 'status', label: '状态', type: 'switch', activeText: '启用', inactiveText: '禁用' },
    { prop: 'remark', label: '备注', type: 'textarea' }
  ];
  const menuCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'menuName', label: '菜单名称', search: true, rule: req('请输入菜单名称') },
    { prop: 'menuType', label: '类型', type: 'select', default: 1, options: [{ label: '目录', value: 0 }, { label: '菜单', value: 1 }, { label: '按钮', value: 2 }] },
    { prop: 'path', label: '路径' },
    { prop: 'component', label: '组件' },
    { prop: 'icon', label: '图标' },
    { prop: 'permission', label: '权限标识' },
    { prop: 'sort', label: '排序', type: 'number' },
    { prop: 'parentId', label: '父ID', type: 'number' }
  ];
  const dictGroupCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'groupCode', label: '分组编码', search: true, rule: req('请输入分组编码') },
    { prop: 'groupName', label: '分组名称', search: true, rule: req('请输入分组名称') },
    { prop: 'remark', label: '备注', type: 'textarea' },
    { prop: 'status', label: '状态', type: 'switch', activeText: '启用', inactiveText: '禁用' }
  ];
  const dictItemCols = [
    { prop: 'id', label: 'ID', hideInForm: true, hideInTable: true },
    { prop: 'groupCode', label: '所属分组', type: 'select', rule: req('请选择分组'), options: () => COC.dictGroups || [] },
    { prop: 'itemName', label: '字典名称', search: true, rule: req('请输入字典名称') },
    { prop: 'itemValue', label: '字典值', search: true, rule: req('请输入字典值') },
    { prop: 'sort', label: '排序', type: 'number' },
    { prop: 'status', label: '状态', type: 'switch', activeText: '启用', inactiveText: '禁用' }
  ];

  /* ============ 通用 CRUD 组件实例 ============ */
  const clanCrud = createCrud({ name: 'ClanCrud', baseUrl: '/api/clan', cols: clanCols });
  const memberCrud = createCrud({ name: 'MemberCrud', baseUrl: '/api/clan/member', cols: memberCols });
  const warCrud = createCrud({ name: 'WarCrud', baseUrl: '/api/war', cols: warCols });
  const warRecordCrud = createCrud({ name: 'WarRecordCrud', baseUrl: '/api/war/record', cols: warRecordCols });
  const leagueCrud = createCrud({ name: 'LeagueCrud', baseUrl: '/api/league', cols: leagueCols });
  const leagueRecordCrud = createCrud({ name: 'LeagueRecordCrud', baseUrl: '/api/league/record', cols: leagueRecordCols });
  const leagueSignupCrud = createCrud({
    name: 'LeagueSignupCrud',
    baseUrl: '/api/league/signup',
    listMode: true,
    lazy: true, // 需先选联赛才加载
    cols: leagueSignupCols
  });
  const groupCrud = createCrud({ name: 'GroupCrud', baseUrl: '/api/clan/group', cols: groupCols });
  const roleCrud = createCrud({ name: 'RoleCrud', baseUrl: '/api/sys/role', cols: roleCols });
  const menuCrud = createCrud({ name: 'MenuCrud', baseUrl: '/api/sys/menu', cols: menuCols });
  const dictGroupCrud = createCrud({ name: 'DictGroupCrud', baseUrl: '/api/dict/group', cols: dictGroupCols });
  const dictItemCrud = createCrud({ name: 'DictItemCrud', baseUrl: '/api/dict/item', cols: dictItemCols });

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
  const NAV = [
    { path: '/dashboard', title: '数据看板', icon: 'Odometer' },
    { path: '/clan', title: '部落管理', icon: 'OfficeBuilding' },
    { path: '/war', title: '部落战管理', icon: 'DataAnalysis' },
    { path: '/league', title: '联赛管理', icon: 'Trophy' },
    { path: '/system', title: '系统管理', icon: 'Setting', perm: 'system:manage' }
  ];

  const Layout = {
    data() {
      return { nav: NAV, pwdVisible: false, pwdForm: { oldPassword: '', newPassword: '' } };
    },
    computed: {
      user() { return COC.store.user || {}; },
      visibleNav() { return this.nav.filter((n) => !n.perm || COC.store.hasPerm(n.perm)); }
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
          <span class="title">{{ (nav.find(n=>active(n.path))||{}).title || '部落冲突部落联赛管理系统' }}</span>
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
  const ClanPage = {
    components: { clanCrud, memberCrud },
    template: `
    <el-tabs class="coc-tabs" v-model="t">
      <el-tab-pane label="部落管理" name="clan"><component :is="'clanCrud'" /></el-tab-pane>
      <el-tab-pane label="部落成员" name="member"><component :is="'memberCrud'" /></el-tab-pane>
    </el-tabs>`,
    data() { return { t: 'clan' }; }
  };
  const WarPage = {
    components: { warCrud, warRecordCrud },
    template: `
    <el-tabs class="coc-tabs" v-model="t">
      <el-tab-pane label="部落战" name="war"><component :is="'warCrud'" /></el-tab-pane>
      <el-tab-pane label="部落战战绩" name="rec"><component :is="'warRecordCrud'" /></el-tab-pane>
    </el-tabs>`,
    data() { return { t: 'war' }; }
  };
  const LeaguePage = {
    components: { leagueCrud, leagueRecordCrud, leagueSignupCrud },
    template: `
    <el-tabs class="coc-tabs" v-model="t">
      <el-tab-pane label="联赛管理" name="lg"><component :is="'leagueCrud'" /></el-tab-pane>
      <el-tab-pane label="联赛战绩" name="rec"><component :is="'leagueRecordCrud'" /></el-tab-pane>
      <el-tab-pane label="联赛报名" name="sign"><component :is="'leagueSignupCrud'" /></el-tab-pane>
    </el-tabs>`,
    data() { return { t: 'lg' }; }
  };

  /* ============ 用户管理（含角色分配） ============ */
  const UserManage = {
    data() {
      return {
        list: [], total: 0, page: 1, size: 10, filters: {}, keyword: '',
        loading: false, dialogVisible: false, dialogTitle: '新增用户', form: {},
        roleDialog: false, roleUserId: null, roleSel: [], savingRole: false
      };
    },
    async mounted() { await this.load(); },
    methods: {
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
        this.form = { username: '', nickname: '', phone: '', email: '', groupNo: '', status: 1, password: '' };
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
        <el-table-column prop="groupNo" label="群组编号" />
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
          <el-form-item label="群组编号"><el-input v-model="form.groupNo" /></el-form-item>
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
          <el-option v-for="r in (COC.roles||[])" :key="r.value" :label="r.label" :value="r.value" />
        </el-select>
        <template #footer><el-button @click="roleDialog=false">取消</el-button><el-button type="primary" :loading="savingRole" @click="saveRole">保存</el-button></template>
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

  /* ============ 系统管理 ============ */
  const SystemPage = {
    components: { groupCrud, UserManage, roleCrud, menuCrud, DictManage },
    template: `
    <el-tabs class="coc-tabs" v-model="t">
      <el-tab-pane label="部落群组" name="g"><component :is="'groupCrud'" /></el-tab-pane>
      <el-tab-pane label="用户管理" name="u"><component :is="'UserManage'" /></el-tab-pane>
      <el-tab-pane label="角色管理" name="r"><component :is="'roleCrud'" /></el-tab-pane>
      <el-tab-pane label="菜单管理" name="m"><component :is="'menuCrud'" /></el-tab-pane>
      <el-tab-pane label="字典管理" name="d"><component :is="'DictManage'" /></el-tab-pane>
    </el-tabs>`,
    data() { return { t: 'g' }; }
  };

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
  router.beforeEach((to) => {
    if (!to.meta.public && !COC.store.token) return '/login';
    if (to.path === '/login' && COC.store.token) return '/dashboard';
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
