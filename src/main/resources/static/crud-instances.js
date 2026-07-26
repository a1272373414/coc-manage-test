/* 通用 CRUD 组件实例：所有 createCrud({...}) 调用
 * 依赖顺序：cols.js → crud.js → crud-instances.js → app.js
 * 暴露到 window.COC_CRUD 上，供 app.js 使用
 */
(function () {
  const {
    clanCols,
    memberCols,
    warCols,
    warRecordCols,
    leagueCols,
    leagueClanScoreCols,
    leagueRecordCols,
    leagueSignupCols,
    groupCols,
    menuCols,
    dictGroupCols,
    dictItemCols,
    configCols,
  } = window.COC_COLS;

  const clanCrud = createCrud({
    name: "ClanCrud",
    baseUrl: "/api/clan",
    cols: clanCols,
    perms: { create: "clan:add", edit: "clan:edit", delete: "clan:delete" },
  });
  // 部落新增：groupNo 自动从当前登录用户取（群主/部落管理员有 groupNo）；
  // 若为空（如超级管理员直接新增），提示需成为群主或部落管理员。
  const _clanOpenCreate = clanCrud.methods.openCreate;
  clanCrud.methods.openCreate = function () {
    var groupNo = (COC.store.user && COC.store.user.groupNo) || "";
    if (!groupNo) {
      if (window.ElementPlus && ElementPlus.ElMessage) {
        ElementPlus.ElMessage.warning("需成为群主或部落管理员才能新增部落");
      }
      return;
    }
    _clanOpenCreate.call(this);
    this.form.groupNo = groupNo;
  };
  // 部落成员导入弹窗模板：选择部落 → 上传 Excel → 解析预览（按名称查重，已存在跳过）→ 确认导入
  const CLAN_MEMBER_IMPORT_DIALOG_TEMPLATE = `
<div class="clan-member-import-dialog">
  <el-dialog v-model="importDialogVisible" title="导入部落成员" width="720px" @closed="onImportClosed">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
      title="导入说明"
      description="选择部落并上传 Excel（含“名称”“编号”“大本等级”“匹配值”“战斗力”五列，后四列均可留空），按成员名称查重，已存在的成员自动跳过。" />

    <el-form label-width="72px" style="margin-bottom:12px">
      <el-form-item label="部落" required>
        <el-select v-model="importClanNo" filterable placeholder="请选择部落" style="width:100%">
          <el-option v-for="o in remoteOptions.clanNo" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-upload
      ref="importUpload"
      :auto-upload="false"
      :limit="1"
      accept=".xlsx,.xls"
      :on-change="onImportFileChange"
      :on-remove="onImportFileRemove"
      :file-list="importFiles">
      <template #trigger>
        <el-button type="primary" plain>选择 Excel</el-button>
      </template>
      <el-button style="margin-left:8px" type="success" :loading="previewLoading" @click="doParseImport">解析预览</el-button>
      <el-link type="primary" style="margin-left:12px" @click="downloadTemplate">下载导入模板</el-link>
    </el-upload>

    <div v-if="previewList.length" style="margin-top:12px">
      <el-table :data="previewList" border size="small" max-height="320">
        <el-table-column type="index" label="#" width="48" />
        <el-table-column label="成员名称" min-width="140">
          <template #default="scope">
            <span :class="scope.row.exists ? 'member-exists-cell' : ''" style="padding:2px 4px;border-radius:4px">{{ scope.row.memberName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="memberNo" label="编号" min-width="120" />
        <el-table-column label="大本等级" min-width="110">
          <template #default="scope">
            <el-input v-model.number="scope.row.thLevel" type="number" size="small" placeholder="可空" />
          </template>
        </el-table-column>
        <el-table-column label="匹配值" min-width="110">
          <template #default="scope">
            <el-input v-model.number="scope.row.matchValue" type="number" size="small" placeholder="可空" />
          </template>
        </el-table-column>
        <el-table-column label="战斗力" min-width="110">
          <template #default="scope">
            <el-input v-model.number="scope.row.combatPower" type="number" size="small" placeholder="可空" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="104">
          <template #default="scope">
            <el-tag v-if="scope.row.exists" type="info" size="small">已存在·跳过</el-tag>
            <el-tag v-else type="success" size="small">新增</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="scope">
            <el-button link type="danger" size="small" @click="removeImportRow(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:10px;text-align:right">
        <span style="margin-right:12px;color:#909399;font-size:12px">
          共 {{ previewList.length }} 条，已存在 {{ previewList.filter(function(r){return r.exists;}).length }} 条将被跳过
        </span>
        <el-button @click="importDialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="confirmLoading" @click="doConfirmImport">确认导入</el-button>
      </div>
    </div>
  </el-dialog>
</div>`;

  // 一键计算战斗力弹窗模板：选择部落 → 配置公式 → 计算并保存
  const CLAN_MEMBER_CALC_DIALOG_TEMPLATE = `
<el-dialog v-model="calcDialogVisible" title="一键计算战斗力" width="620px" :close-on-click-modal="false">
  <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
    title="说明"
    description="总分数为 10000，请配置各项得分分配（四项之和建议为 10000）。点击计算后，将按下方公式算出所选部落每个成员的战斗力并保存到数据库。" />

  <div class="calc-formula-box">
    <div class="calc-formula-title">计算逻辑（按成员分别统计）</div>
    <div class="calc-formula-row"><span class="calc-formula-k">进攻概率</span> = 实际进攻总次数 ÷ 应进攻总次数<span class="calc-formula-note">（查联赛成员战绩表）</span></div>
    <div class="calc-formula-row"><span class="calc-formula-k">参赛概率</span> = 应进攻次数&gt;0 的战绩条数 ÷ 战绩总条数（参赛次数）</div>
    <div class="calc-formula-row"><span class="calc-formula-k">三星概率</span> = 总星数 ÷（总应进攻次数 × 3）<span class="calc-formula-note">（每次进攻最多 3 星，归一到 0~1）</span></div>
    <div class="calc-formula-row"><span class="calc-formula-k">防御概率</span> = 大本等级 ÷ 最高大本等级 × 50% + 匹配值 ÷ 最高匹配值 × 50%</div>
    <div class="calc-formula-row calc-formula-result"><span class="calc-formula-k">战斗力</span> = 进攻概率 × 进攻得分 + 参赛概率 × 参赛得分 + 三星概率 × 三星得分 + 防御概率 × 防御得分<span class="calc-formula-note">（结果取整）</span></div>
    <div class="calc-formula-ref">参考配置：最高大本等级 = {{ calcInfo.maxThLevel }}，最高匹配值 = {{ calcInfo.maxMatchValue }}</div>
  </div>

  <el-form label-width="120px" style="margin-top:12px">
    <el-form-item label="部落" required>
      <el-select v-model="calcClanNo" filterable clearable placeholder="请选择部落" style="width:100%">
        <el-option v-for="o in (remoteOptions.clanNo||[])" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
    </el-form-item>
    <el-form-item label="进攻概率得分">
      <el-input-number v-model="calcScores.attackScore" :min="0" :step="100" style="width:100%" />
    </el-form-item>
    <el-form-item label="参赛概率得分">
      <el-input-number v-model="calcScores.participateScore" :min="0" :step="100" style="width:100%" />
    </el-form-item>
    <el-form-item label="三星概率得分">
      <el-input-number v-model="calcScores.threeStarScore" :min="0" :step="100" style="width:100%" />
    </el-form-item>
    <el-form-item label="防御概率得分">
      <el-input-number v-model="calcScores.defenseScore" :min="0" :step="100" style="width:100%" />
    </el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="calcDialogVisible=false">取消</el-button>
    <el-button type="primary" :loading="calcLoading" @click="doCalcCombat">计算</el-button>
  </template>
</el-dialog>`;

  const memberCrud = createCrud({
    name: "MemberCrud",
    baseUrl: "/api/clan/member",
    cols: memberCols,
    perms: {
      create: "clan:member:add",
      edit: "clan:member:edit",
      delete: "clan:member:delete",
    },
    extraButtons: [
      {
        text: "导入成员",
        type: "primary",
        click: "openImport",
        perm: "clan:member:import",
      },
      {
        text: "一键计算战斗力",
        type: "warning",
        click: "openCalcCombat",
        perm: "clan:member:calc",
      },
    ],
    extraTemplate:
      CLAN_MEMBER_IMPORT_DIALOG_TEMPLATE + CLAN_MEMBER_CALC_DIALOG_TEMPLATE,
  });
  // 部落成员导入弹窗所需的 data 字段（保留基类默认 data）
  var _memberOrigData = memberCrud.data;
  memberCrud.data = function () {
    var d = _memberOrigData.call(this);
    d.importDialogVisible = false;
    d.importClanNo = "";
    d.importFiles = [];
    d.previewList = [];
    d.previewLoading = false;
    d.confirmLoading = false;
    // 一键计算战斗力弹窗字段
    d.calcDialogVisible = false;
    d.calcClanNo = "";
    d.calcScores = {
      attackScore: 2500,
      participateScore: 2500,
      threeStarScore: 2500,
      defenseScore: 2500,
    };
    d.calcInfo = { maxThLevel: 17, maxMatchValue: 0 };
    d.calcLoading = false;
    return d;
  };

  // 打开导入弹窗：预填当前筛选的部落
  memberCrud.methods.openImport = function () {
    // 确保“已存在”成员名称的绿色高亮样式已注入（仅一次）
    if (!document.getElementById("clan-member-import-green-style")) {
      var s = document.createElement("style");
      s.id = "clan-member-import-green-style";
      s.textContent =
        ".member-exists-cell{background:#f0f9eb;border:1px solid #67c23a;border-radius:4px;color:#2e7d32;font-weight:700;}";
      document.head.appendChild(s);
    }
    this.importClanNo = (this.filters && this.filters.clanNo) || "";
    this.importFiles = [];
    this.previewList = [];
    this.previewLoading = false;
    this.confirmLoading = false;
    this.importDialogVisible = true;
  };

  // 关闭弹窗后重置状态
  memberCrud.methods.onImportClosed = function () {
    this.importClanNo = "";
    this.importFiles = [];
    this.previewList = [];
  };

  memberCrud.methods.onImportFileChange = function (file, fileList) {
    this.importFiles = fileList;
  };
  memberCrud.methods.onImportFileRemove = function (file, fileList) {
    this.importFiles = fileList;
  };

  // 解析预览：将 Excel 发给后台，按成员名称查重并标注 exists
  memberCrud.methods.doParseImport = async function () {
    if (!this.importClanNo) {
      ElementPlus.ElMessage.warning("请先选择部落");
      return;
    }
    if (!this.importFiles.length) {
      ElementPlus.ElMessage.warning("请选择 Excel 文件");
      return;
    }
    this.previewLoading = true;
    try {
      var fd = new FormData();
      fd.append("type", "excel");
      fd.append("clanNo", this.importClanNo);
      var raw = this.importFiles[0].raw;
      if (raw) fd.append("files", raw);
      var res = await COC.api.clanMemberImportPreview(fd);
      this.previewList = (res && res.records) || [];
      // 同步后台返回的 clanNo/groupNo（预览时服务端已按当前群组处理）
      if (res && res.clanNo) this.importClanNo = res.clanNo;
      ElementPlus.ElMessage.success(
        "解析完成，共 " + this.previewList.length + " 条",
      );
    } catch (e) {
      ElementPlus.ElMessage.error(
        "解析失败：" +
          ((e && e.response && e.response.data && e.response.data.message) ||
            "请检查文件格式"),
      );
    } finally {
      this.previewLoading = false;
    }
  };

  memberCrud.methods.removeImportRow = function (index) {
    this.previewList.splice(index, 1);
  };

  // 下载导入模板
  memberCrud.methods.downloadTemplate = async function () {
    try {
      var blob = await COC.api.clanMemberImportTemplate();
      var url = window.URL.createObjectURL(blob);
      var a = document.createElement("a");
      a.href = url;
      a.download = "部落成员导入模板.xlsx";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (e) {
      ElementPlus.ElMessage.error("下载模板失败");
    }
  };

  // 确认导入：跳过已存在成员，其余入库
  memberCrud.methods.doConfirmImport = async function () {
    if (!this.importClanNo) {
      ElementPlus.ElMessage.warning("请先选择部落");
      return;
    }
    if (!this.previewList.length) {
      ElementPlus.ElMessage.warning("无导入数据");
      return;
    }
    this.confirmLoading = true;
    try {
      var res = await COC.api.clanMemberImportConfirm({
        clanNo: this.importClanNo,
        records: this.previewList.map(function (r) {
          var rec = { memberName: r.memberName, memberNo: r.memberNo };
          if (r.thLevel !== "" && r.thLevel !== null && r.thLevel !== undefined)
            rec.thLevel = r.thLevel;
          if (
            r.matchValue !== "" &&
            r.matchValue !== null &&
            r.matchValue !== undefined
          )
            rec.matchValue = r.matchValue;
          if (
            r.combatPower !== "" &&
            r.combatPower !== null &&
            r.combatPower !== undefined
          )
            rec.combatPower = r.combatPower;
          return rec;
        }),
      });
      var inserted = (res && res.inserted) || 0;
      var skipped = (res && res.skipped) || 0;
      var updated = (res && res.updated) || 0;
      ElementPlus.ElMessage.success(
        "导入完成：新增 " +
          inserted +
          " 条" +
          (updated ? "，更新 " + updated + " 条" : "") +
          (skipped ? "，跳过 " + skipped + " 条" : ""),
      );
      this.importDialogVisible = false;
      this.load();
    } catch (e) {
      ElementPlus.ElMessage.error(
        "导入失败：" +
          ((e && e.response && e.response.data && e.response.data.message) ||
            ""),
      );
    } finally {
      this.confirmLoading = false;
    }
  };

  // 一键计算战斗力：弹窗选部落 + 配置公式（得分分配从系统配置表读取默认值）
  memberCrud.methods.openCalcCombat = async function () {
    this.calcClanNo = (this.filters && this.filters.clanNo) || "";
    this.calcScores = {
      attackScore: 2500,
      participateScore: 2500,
      threeStarScore: 2500,
      defenseScore: 2500,
    };
    this.calcInfo = { maxThLevel: 17, maxMatchValue: 0 };
    this.calcDialogVisible = true;
    try {
      var c = await COC.api.combatPowerConfig();
      if (c) {
        this.calcScores = {
          attackScore: c.attackScore != null ? c.attackScore : 2500,
          participateScore:
            c.participateScore != null ? c.participateScore : 2500,
          threeStarScore: c.threeStarScore != null ? c.threeStarScore : 2500,
          defenseScore: c.defenseScore != null ? c.defenseScore : 2500,
        };
        this.calcInfo = {
          maxThLevel: c.maxThLevel != null ? c.maxThLevel : 17,
          maxMatchValue: c.maxMatchValue != null ? c.maxMatchValue : 0,
        };
      }
    } catch (e) {
      /* 使用默认值即可 */
    }
  };

  // 计算并保存到数据库，关闭弹窗后刷新列表
  memberCrud.methods.doCalcCombat = async function () {
    if (!this.calcClanNo) {
      ElementPlus.ElMessage.warning("请选择部落");
      return;
    }
    this.calcLoading = true;
    try {
      var res = await COC.api.combatPowerCalculate({
        clanNo: this.calcClanNo,
        attackScore: Number(this.calcScores.attackScore),
        participateScore: Number(this.calcScores.participateScore),
        threeStarScore: Number(this.calcScores.threeStarScore),
        defenseScore: Number(this.calcScores.defenseScore),
      });
      var updated = (res && res.updated) || 0;
      ElementPlus.ElMessage.success("计算完成，已更新 " + updated + " 名成员");
      this.calcDialogVisible = false;
      await this.load();
    } catch (e) {
      ElementPlus.ElMessage.error(
        "计算失败：" +
          ((e && e.response && e.response.data && e.response.data.message) ||
            (e && e.message) ||
            ""),
      );
    } finally {
      this.calcLoading = false;
    }
  };

  const warCrud = createCrud({
    name: "WarCrud",
    baseUrl: "/api/war",
    cols: warCols,
    perms: { create: "war:add", edit: "war:edit", delete: "war:delete" },
  });
  const warRecordCrud = createCrud({
    name: "WarRecordCrud",
    baseUrl: "/api/war/record",
    cols: warRecordCols,
    perms: {
      create: "war:record:add",
      edit: "war:record:edit",
      delete: "war:record:delete",
    },
  });
  const leagueCrud = createCrud({
    name: "LeagueCrud",
    baseUrl: "/api/league",
    cols: leagueCols,
    perms: {
      create: "league:add",
      edit: "league:edit",
      delete: "league:delete",
    },
  });
  const leagueClanScoreCrud = createCrud({
    name: "LeagueClanScoreCrud",
    baseUrl: "/api/league/score",
    cols: leagueClanScoreCols,
    perms: {
      create: "league:score:add",
      edit: "league:score:edit",
      delete: "league:score:delete",
    },
  });
  // 联赛新增：自动填入联赛名称和编号
  // leagueName 格式 "yyyy年M月联赛"，leagueNo 格式 "yyyyMMdd"
  // 日期规则：
  //   日 > 20：月份 +1，日期设为 01（如 7月23日 → 8月01日）
  //   10 <= 日 <= 20：日期设为 15（如 7月15日 → 7月15日）
  //   日 < 10：日期设为 01（如 7月5日 → 7月01日）
  const _leagueOpenCreate = leagueCrud.methods.openCreate;
  leagueCrud.methods.openCreate = function () {
    _leagueOpenCreate.call(this);
    var now = new Date();
    var year = now.getFullYear();
    var month = now.getMonth() + 1; // JS 月份从 0 开始
    var day = now.getDate();
    var dd;
    if (day > 20) {
      month++;
      if (month > 12) {
        month = 1;
        year++;
      }
      dd = "01";
    } else if (day >= 10) {
      dd = "15";
    } else {
      dd = "01";
    }
    var mm = month < 10 ? "0" + month : "" + month;
    this.form.leagueName = year + "年" + month + "月联赛";
    this.form.leagueNo = "" + year + mm + dd;
    // 报名开始：当前时间；报名结束：当前时间 +5 天
    var fmt = function (d) {
      var y = d.getFullYear();
      var mo = d.getMonth() + 1;
      var da = d.getDate();
      var h = d.getHours();
      var mi = d.getMinutes();
      var s = d.getSeconds();
      var p = function (n) {
        return n < 10 ? "0" + n : "" + n;
      };
      return (
        y + "-" + p(mo) + "-" + p(da) + " " + p(h) + ":" + p(mi) + ":" + p(s)
      );
    };
    this.form.signupStart = fmt(now);
    var end = new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000);
    this.form.signupEnd = fmt(end);
  };
  // 导入弹窗模板：选择方式 → 上传/解析 → 预览编辑 → 确认导入
  const IMPORT_DIALOG_TEMPLATE = `
<el-dialog v-model="importDialogVisible" title="导入联赛战绩" width="1200px" :close-on-click-modal="false" destroy-on-close>
    <el-form label-width="90px" style="margin-bottom:8px">
      <el-form-item label="所属群组" required>
        <el-input :model-value="importGroupLabel" disabled placeholder="-" />
      </el-form-item>
      <el-form-item label="所属联赛" required>
        <el-select v-model="importLeagueNo" filterable clearable placeholder="请选择联赛" style="width:100%">
          <el-option v-for="o in (remoteOptions.leagueNo||[])" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属部落" required>
        <el-select v-model="importClanNo" filterable clearable placeholder="请选择部落" style="width:100%">
          <el-option v-for="o in (remoteOptions.clanNo||[])" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
    </el-form>

    <div v-if="importStep==='select'">
      <div style="margin:8px 0 12px;color:#909399;font-size:13px;">请选择导入方式：</div>
      <div style="display:flex;gap:16px;flex-wrap:wrap;">
        <el-button @click="chooseImportType('image')" style="width:160px;height:80px;">
          <div>图片导入</div>
          <div style="font-size:12px;color:#909399;margin-top:4px;">可多张，按数字命名</div>
        </el-button>
        <el-button @click="chooseImportType('excel')" style="width:160px;height:80px;">
          <div>Excel 导入</div>
          <div style="font-size:12px;color:#909399;margin-top:4px;">.xlsx 文件</div>
        </el-button>
        <el-button @click="chooseImportType('json')" style="width:160px;height:80px;">
          <div>JSON 导入</div>
          <div style="font-size:12px;color:#909399;margin-top:4px;">本地解析</div>
        </el-button>
      </div>
      <div style="margin-top:16px;">
        <el-button link type="primary" @click="downloadTemplate('excel')">下载 Excel 示例</el-button>
        <el-button link type="primary" @click="downloadTemplate('json')">下载 JSON 示例</el-button>
      </div>
    </div>

    <div v-if="importStep==='upload'">
      <div v-if="importType==='image'" style="margin-bottom:8px;color:#E6A23C;font-size:13px;">
        请选择多张图片，图片需按顺序以数字命名（如 1.jpg、2.jpg ...）
      </div>
      <el-upload
        :auto-upload="false"
        :on-change="onImportFileChange"
        :on-remove="onImportFileRemove"
        :multiple="importType==='image'"
        :accept="importType==='image' ? 'image/*' : '.xlsx,.xls'"
        :file-list="importFiles"
        :limit="importType==='image' ? 50 : 1">
        <el-button type="primary">选择{{ importType==='image' ? '图片' : 'Excel' }}</el-button>
      </el-upload>
      <div style="margin-top:16px;">
        <el-button @click="importStep='select'">上一步</el-button>
        <el-button type="success" :loading="previewLoading" :disabled="!importFiles.length" @click="doParseImport">上传并解析</el-button>
      </div>
    </div>

    <div v-if="importStep==='json'">
      <div style="margin-bottom:8px;color:#909399;font-size:13px;">粘贴 JSON 文本，或选择 .json 文件本地解析：</div>
      <el-input v-model="importJsonText" type="textarea" :rows="6" placeholder='{"data":[{"rank":1,"name":"成员","stars":21,"destruction":700.0,"attacks":"7/7"}]}' />
      <el-upload
        :auto-upload="false"
        :on-change="onJsonFileChange"
        accept=".json"
        :show-file-list="false"
        style="margin-top:8px;display:inline-block">
        <el-button>选择 .json 文件</el-button>
      </el-upload>
      <div style="margin-top:16px;">
        <el-button @click="importStep='select'">上一步</el-button>
        <el-button type="success" :loading="previewLoading" :disabled="!importJsonText" @click="doParseJson">解析 JSON</el-button>
      </div>
    </div>

    <div v-if="importStep==='preview'">
      <div style="margin-bottom:8px;color:#909399;font-size:13px;">共 {{ previewList.length }} 条，可编辑后确认导入：</div>
        <el-table :data="previewList" border stripe max-height="420" size="small" style="width:100%;table-layout:fixed">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="排名" width="72">
          <template #default="{row}"><el-input v-model="row.rank" size="small" /></template>
        </el-table-column>
        <el-table-column label="成员名称" min-width="160">
          <template #default="{row}">
            <div :class="row.memberExists ? 'member-exists-cell' : ''" :title="row.memberExists ? '该成员已在部落成员表中' : ''">
              <el-input v-model="row.memberName" size="small" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="胜利之星" width="100">
          <template #default="{row}"><el-input-number v-model="row.winStars" :min="0" controls-position="right" size="small" /></template>
        </el-table-column>
        <el-table-column label="摧毁率" width="100">
          <template #default="{row}"><el-input-number v-model="row.destroyRate" :min="0" controls-position="right" size="small" /></template>
        </el-table-column>
        <el-table-column label="是否有额外" width="110">
          <template #default="{row}">
            <el-select v-model="row.hasExtra" size="small" style="width:100%">
              <el-option :value="1" label="是" />
              <el-option :value="0" label="否" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="实际进攻" width="100">
          <template #default="{row}"><el-input-number v-model="row.actualAttacks" :min="0" controls-position="right" size="small" /></template>
        </el-table-column>
        <el-table-column label="应进攻" width="100">
          <template #default="{row}"><el-input-number v-model="row.requiredAttacks" :min="0" controls-position="right" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{row,$index}"><el-button link type="danger" size="small" @click="previewList.splice($index,1)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;">
        <el-button @click="importStep='select'">重新选择</el-button>
        <el-button type="primary" :loading="confirmLoading" @click="doConfirmImport">确认导入</el-button>
      </div>
    </div>
  </el-dialog>`;

  const leagueRecordCrud = createCrud({
    name: "LeagueRecordCrud",
    baseUrl: "/api/league/record",
    cols: leagueRecordCols,
    perms: {
      create: "league:record:add",
      edit: "league:record:edit",
      delete: "league:record:delete",
    },
    extraButtons: [
      {
        text: "导入",
        type: "primary",
        click: "openImport",
        perm: "league:record:import",
      },
    ],
    extraTemplate: IMPORT_DIALOG_TEMPLATE,
  });

  // 导入弹窗所需的 data 字段
  var _recOrigData = leagueRecordCrud.data;
  leagueRecordCrud.data = function () {
    var d = _recOrigData.call(this);
    d.importDialogVisible = false;
    d.importType = ""; // image | excel | json
    d.importStep = "select"; // select | upload | json | preview
    d.importFiles = []; // el-upload 选中的文件
    d.importJsonText = ""; // JSON 粘贴文本
    d.previewList = []; // 预览数据
    d.previewLoading = false;
    d.confirmLoading = false;
    d.importLeagueNo = "";
    d.importClanNo = "";
    d.importGroupNo = "";
    d.importGroupLabel = "";
    return d;
  };

  // 确保命中成员的绿色样式已注入全局（只注入一次）
  function ensureImportStyle() {
    if (document.getElementById("league-import-green-style")) return;
    var s = document.createElement("style");
    s.id = "league-import-green-style";
    s.textContent =
      "" +
      ".member-exists-cell{background:#f0f9eb;border:1px solid #67c23a;border-radius:4px;padding:2px 2px;}" +
      ".member-exists-cell .el-input__wrapper{background-color:transparent!important;box-shadow:none!important;}" +
      ".member-exists-cell .el-input__inner{color:#2e7d32!important;font-weight:700;}";
    document.head.appendChild(s);
  }

  // 打开导入弹窗：预填当前筛选的联赛/部落与登录用户群组，并加载群组下拉
  leagueRecordCrud.methods.openImport = async function () {
    ensureImportStyle();
    this.importLeagueNo = (this.filters && this.filters.leagueNo) || "";
    this.importClanNo = (this.filters && this.filters.clanNo) || "";
    this.importGroupNo = (COC.store.user && COC.store.user.groupNo) || "";
    this.importType = "";
    this.importStep = "select";
    this.previewList = [];
    this.importFiles = [];
    this.importJsonText = "";
    this.importDialogVisible = true;
    if (!this.remoteOptions.groupNo || !this.remoteOptions.groupNo.length) {
      try {
        var r = await COC.api.page("/api/clan/group", { size: 9999 });
        this.remoteOptions.groupNo = (r.records || []).map(function (x) {
          return { label: x.groupName || x.groupNo, value: x.groupNo };
        });
      } catch (e) {
        /* ignore */
      }
    }
    // 用登录用户的 groupNo 反查群组名作为只读展示；查不到则回退为 groupNo
    var hit = (this.remoteOptions.groupNo || []).find(
      (x) => x.value === this.importGroupNo,
    );
    this.importGroupLabel = (hit && hit.label) || this.importGroupNo;
  };

  leagueRecordCrud.methods.chooseImportType = function (t) {
    this.importType = t;
    this.importFiles = [];
    this.importJsonText = "";
    this.previewList = [];
    this.importStep = t === "json" ? "json" : "upload";
  };

  leagueRecordCrud.methods.onImportFileChange = function (file, fileList) {
    this.importFiles = fileList;
  };
  leagueRecordCrud.methods.onImportFileRemove = function (file, fileList) {
    this.importFiles = fileList;
  };

  leagueRecordCrud.methods.onJsonFileChange = function (file) {
    var self = this;
    var reader = new FileReader();
    reader.onload = function (e) {
      self.importJsonText = e.target.result;
    };
    reader.readAsText(file.raw);
  };

  leagueRecordCrud.methods.doParseImport = async function () {
    if (!this.importLeagueNo || !this.importClanNo || !this.importGroupNo) {
      ElementPlus.ElMessage.warning("请先选择联赛/部落/群组");
      return;
    }
    if (!this.importFiles.length) {
      ElementPlus.ElMessage.warning("请选择文件");
      return;
    }
    this.previewLoading = true;
    try {
      var fd = new FormData();
      fd.append("type", this.importType);
      fd.append("leagueNo", this.importLeagueNo);
      fd.append("clanNo", this.importClanNo);
      fd.append("groupNo", this.importGroupNo);
      for (var i = 0; i < this.importFiles.length; i++) {
        var raw = this.importFiles[i].raw;
        if (raw) fd.append("files", raw);
      }
      var res = await COC.api.leagueImportPreview(fd);
      this.previewList = (res && res.records) || [];
      this.importStep = "preview";
      ElementPlus.ElMessage.success(
        "解析完成，共 " + this.previewList.length + " 条",
      );
    } catch (e) {
      ElementPlus.ElMessage.error("解析失败");
    } finally {
      this.previewLoading = false;
    }
  };

  leagueRecordCrud.methods.doParseJson = function () {
    if (!this.importLeagueNo || !this.importClanNo || !this.importGroupNo) {
      ElementPlus.ElMessage.warning("请先选择联赛/部落/群组");
      return;
    }
    if (!this.importJsonText) {
      ElementPlus.ElMessage.warning("请粘贴或选择 JSON");
      return;
    }
    var parsed;
    try {
      parsed = JSON.parse(this.importJsonText);
    } catch (e) {
      ElementPlus.ElMessage.error("JSON 格式错误");
      return;
    }
    // 兼容脚本输出：{metadata, data:[{...}], records:[[...]]}，以及纯数组
    var arr = parsed.data || parsed.records || parsed;
    if (!Array.isArray(arr)) {
      ElementPlus.ElMessage.error("未识别到数据数组");
      return;
    }
    var self = this;
    this.previewList = arr.map(function (item) {
      // 兼容对象 {rank,name,stars,destruction,attacks} 或数组 [rank,name,stars,destruction,attacks]
      var obj = Array.isArray(item)
        ? {
            rank: item[0],
            name: item[1],
            stars: item[2],
            destruction: item[3],
            attacks: item[4],
          }
        : item;
      // 进攻次数规范化（兼容 attacks:"7/7" 或 actualAttacks/requiredAttacks 两个字段）
      var atkRaw =
        obj.attacks != null
          ? obj.attacks
          : obj.actualAttacks != null && obj.requiredAttacks != null
            ? obj.actualAttacks + "/" + obj.requiredAttacks
            : "";
      var atkNorm = self.normalizeAttacks(atkRaw) || atkRaw;
      var atk = String(atkNorm || "").split("/");
      var actual = Number(atk[0]) || 0;
      var required = Number(atk[1]) || 0;
      var winStars = Number(obj.stars != null ? obj.stars : obj.winStars);
      if (isNaN(winStars)) winStars = 0;
      var destroyRate = Number(
        obj.destruction != null ? obj.destruction : obj.destroyRate,
      );
      if (isNaN(destroyRate)) destroyRate = 0;
      // 未参战行(0/0 或 0/1) → 胜利之星/摧毁率补0
      if (
        (actual === 0 && required === 0) ||
        (actual === 0 && required === 1)
      ) {
        winStars = 0;
        destroyRate = 0;
      }
      return {
        rank: obj.rank != null ? String(obj.rank) : "",
        memberName: obj.name || obj.memberName || "",
        winStars: winStars,
        destroyRate: Math.round(destroyRate),
        actualAttacks: actual,
        requiredAttacks: required,
        hasExtra: 0,
        signupStatus: null,
        leagueNo: self.importLeagueNo,
        clanNo: self.importClanNo,
        groupNo: self.importGroupNo,
      };
    });
    this.importStep = "preview";
    ElementPlus.ElMessage.success(
      "解析完成，共 " + this.previewList.length + " 条",
    );
    // JSON 本地解析后，查询后台判断成员是否存在，回填 memberExists
    this.checkMemberExists();
  };

  // 查询后台：批量判断 previewList 中的成员名是否存在于所选部落+群组，回填 memberExists
  leagueRecordCrud.methods.checkMemberExists = async function () {
    if (!this.importClanNo || !this.importGroupNo || !this.previewList.length)
      return;
    var names = [];
    for (var i = 0; i < this.previewList.length; i++) {
      var nm = (this.previewList[i].memberName || "").trim();
      if (nm && names.indexOf(nm) === -1) names.push(nm);
    }
    if (!names.length) return;
    try {
      var res = await COC.api.leagueCheckMembers({
        clanNo: this.importClanNo,
        groupNo: this.importGroupNo,
        names: names,
      });
      var exists = (res && res.exists) || [];
      for (var j = 0; j < this.previewList.length; j++) {
        var r = this.previewList[j];
        r.memberExists = exists.indexOf((r.memberName || "").trim()) !== -1;
      }
    } catch (e) {
      /* 忽略，不影响预览 */
    }
  };

  // 进攻次数规范化：标准 X/Y 或 OCR 误读(777→7/7, 000→0/0)；无法识别返回 null
  leagueRecordCrud.methods.normalizeAttacks = function (val) {
    if (val == null) return null;
    var s = String(val).trim();
    var m = s.match(/^(\d+)\s*\/\s*(\d+)$/);
    if (m) return m[1] + "/" + m[2];
    m = s.match(/^(\d)\1{1,2}$/);
    if (m && s.length <= 3) return m[1] + "/" + m[1];
    return null;
  };

  leagueRecordCrud.methods.downloadTemplate = async function (type) {
    try {
      var blob = await COC.api.leagueImportTemplate(type);
      var ext = type === "json" ? "json" : "xlsx";
      var url = window.URL.createObjectURL(blob);
      var a = document.createElement("a");
      a.href = url;
      a.download = "league-record-example." + ext;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (e) {
      ElementPlus.ElMessage.error("下载示例失败");
    }
  };

  leagueRecordCrud.methods.doConfirmImport = async function () {
    if (!this.importLeagueNo || !this.importClanNo || !this.importGroupNo) {
      ElementPlus.ElMessage.warning("请先选择联赛/部落/群组");
      return;
    }
    if (!this.previewList.length) {
      ElementPlus.ElMessage.warning("无导入数据");
      return;
    }
    this.confirmLoading = true;
    try {
      var res = await COC.api.leagueImportConfirm({
        leagueNo: this.importLeagueNo,
        clanNo: this.importClanNo,
        groupNo: this.importGroupNo,
        records: this.previewList,
      });
      ElementPlus.ElMessage.success(
        "导入成功：" +
          ((res && res.inserted) || this.previewList.length) +
          " 条",
      );
      this.importDialogVisible = false;
      this.load();
    } catch (e) {
      ElementPlus.ElMessage.error("导入失败");
    } finally {
      this.confirmLoading = false;
    }
  };
  const leagueSignupCrud = createCrud({
    name: "LeagueSignupCrud",
    baseUrl: "/api/league/signup",
    cols: leagueSignupCols,
    perms: {
      create: "league:signup:add",
      edit: "league:signup:edit",
      delete: "league:signup:delete",
    },
    extraButtons: [
      {
        text: "一键初始化报名",
        type: "warning",
        click: "initSignup",
        perm: "league:signup:init",
      },
    ],
  });
  // 首次加载时自动选最新联赛（id 最大 = 最新），设入搜索栏后查询该联赛报名数据
  var _signupOriginalLoad = leagueSignupCrud.methods.load;
  leagueSignupCrud.methods.load = async function () {
    if (!this._autoLeaguePicked) {
      this._autoLeaguePicked = true;
      // remoteOptions.leagueNo 由 preloadRemoteOptions() 在 mounted 中预加载，
      // BaseCrudController.page 默认按 id DESC 排序，第一条即最新联赛
      var leagues = this.remoteOptions.leagueNo || [];
      if (leagues.length > 0 && !(this.filters && this.filters.leagueNo)) {
        this.filters = this.filters || {};
        this.filters.leagueNo = leagues[0].value;
      }
    }
    await _signupOriginalLoad.call(this);
  };
  // 一键初始化报名：弹窗选部落 → 调 /init 接口 → 重新加载列表
  leagueSignupCrud.methods.initSignup = function () {
    this.initLeagueNo = "";
    this.initClanNo = "";
    this.initDialogVisible = true;
  };
  leagueSignupCrud.methods.doInitSignup = async function () {
    if (!this.initLeagueNo) {
      if (window.ElementPlus && ElementPlus.ElMessage) {
        ElementPlus.ElMessage.warning("请选择联赛");
      }
      return;
    }
    if (!this.initClanNo) {
      if (window.ElementPlus && ElementPlus.ElMessage) {
        ElementPlus.ElMessage.warning("请选择部落");
      }
      return;
    }
    this.initLoading = true;
    try {
      var result = await COC.http.post("/api/league/signup/init", null, {
        params: { leagueNo: this.initLeagueNo, clanNo: this.initClanNo },
      });
      var data = result || {};
      if (window.ElementPlus && ElementPlus.ElMessage) {
        var msg =
          "初始化完成：新增 " +
          (data.inserted || 0) +
          " 条，替换 " +
          (data.replaced || 0) +
          " 条，跳过 " +
          (data.skipped || 0) +
          " 条";
        ElementPlus.ElMessage.success(msg);
      }
      this.initDialogVisible = false;
      // 初始化后自动筛选当前联赛
      this.filters = this.filters || {};
      this.filters.leagueNo = this.initLeagueNo;
      await this.load();
    } catch (e) {
      if (window.ElementPlus && ElementPlus.ElMessage) {
        ElementPlus.ElMessage.error("初始化失败：" + ((e && e.message) || ""));
      }
    } finally {
      this.initLoading = false;
    }
  };
  const groupCrud = createCrud({
    name: "GroupCrud",
    baseUrl: "/api/clan/group",
    cols: groupCols,
    perms: { create: "group:add", edit: "group:edit", delete: "group:delete" },
  });
  // 菜单管理：使用专用树视图组件（替代通用 CRUD 列表），更适合层级数据维护。
  const menuTree = window.createMenuTree();
  const dictGroupCrud = createCrud({
    name: "DictGroupCrud",
    baseUrl: "/api/dict/group",
    cols: dictGroupCols,
    perms: {
      create: "sys:dict:add",
      edit: "sys:dict:edit",
      delete: "sys:dict:delete",
    },
  });
  const dictItemCrud = createCrud({
    name: "DictItemCrud",
    baseUrl: "/api/dict/item",
    cols: dictItemCols,
    perms: {
      create: "sys:dict:add",
      edit: "sys:dict:edit",
      delete: "sys:dict:delete",
    },
  });
  const configCrud = createCrud({
    name: "ConfigCrud",
    baseUrl: "/api/sys/config",
    cols: configCols,
    perms: {
      create: "sys:config:add",
      edit: "sys:config:edit",
      delete: "sys:config:delete",
    },
  });

  // 暴露到全局，供 app.js 使用
  window.COC_CRUD = {
    clanCrud,
    memberCrud,
    warCrud,
    warRecordCrud,
    leagueCrud,
    leagueClanScoreCrud,
    leagueRecordCrud,
    leagueSignupCrud,
    groupCrud,
    menuTree,
    dictGroupCrud,
    dictItemCrud,
    configCrud,
  };
})();
