package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.entity.biz.LeagueRecord;
import com.tencent.wxcloudrun.entity.biz.LeagueSignup;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import com.tencent.wxcloudrun.mapper.LeagueRecordMapper;
import com.tencent.wxcloudrun.mapper.LeagueSignupMapper;
import com.tencent.wxcloudrun.util.MemberNoGenerator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部落成员批量导入。 支持三种导入方式：Excel 导入、根据联赛成员战绩导入、根据联赛报名数据导入。
 * 导入时按成员名称（member_name，含别名）在整个群组(group_no)范围内查重，已存在则忽略（跳过）；不限制同一部落。
 */
@RestController
@RequestMapping("/api/clan/member/import")
public class ClanMemberImportController {

	@Autowired
	private ClanMemberMapper clanMemberMapper;

	@Autowired
	private LeagueRecordMapper leagueRecordMapper;

	@Autowired
	private LeagueSignupMapper leagueSignupMapper;

	/**
	 * 预览。 支持三种导入方式：
	 * <ul>
	 *   <li>excel：解析上传的 Excel 并标注每条记录是否已存在（按成员名称查重）。</li>
	 *   <li>leagueRecord：根据联赛成员战绩导入，查询联赛成员战绩表中「部落成员表还没有」的人。</li>
	 *   <li>leagueSignup：根据联赛报名数据导入，查询联赛报名表中「部落成员表还没有」的人。</li>
	 * </ul>
	 */
	@PostMapping("/preview")
	public ApiResponse preview(@RequestParam("type") String type,
			@RequestParam(value = "leagueNo", required = false) String leagueNo,
			@RequestParam(value = "clanNo", required = false) String clanNo,
			@RequestParam(value = "groupNo", required = false) String groupNo,
			@RequestParam(value = "files", required = false) MultipartFile file) {
		if (!"excel".equalsIgnoreCase(type) && !"leagueRecord".equalsIgnoreCase(type)
				&& !"leagueSignup".equalsIgnoreCase(type)) {
			return ApiResponse.error("不支持的导入方式");
		}
		if (clanNo == null || clanNo.trim().isEmpty()) {
			return ApiResponse.error("请选择部落");
		}
		String g = resolveGroupNo(groupNo);
		if (g == null || g.trim().isEmpty()) {
			return ApiResponse.error("无法获取群组信息，请确认登录状态");
		}

		List<ClanMemberRow> rows;
		if ("excel".equalsIgnoreCase(type)) {
			if (file == null || file.isEmpty()) {
				return ApiResponse.error("请上传 Excel 文件");
			}
			try {
				rows = parseExcel(file.getInputStream());
			}
			catch (Exception e) {
				return ApiResponse.error("Excel 解析失败：" + e.getMessage());
			}
			if (rows.isEmpty()) {
				return ApiResponse.error("未从 Excel 中解析到成员数据");
			}

		// 批量拉取该群组下已存在的成员名称/编号，本地按“编号/名称”条件查重（整个群组范围，不限制同一部落）
		ExistSets exist = loadExist(g);
		Map<String, Long> nameCount = countByNameMap(g);

		for (ClanMemberRow row : rows) {
			boolean hasNo = row.memberNo != null && !row.memberNo.trim().isEmpty();
			ClanMember matched = hasNo ? exist.byNo.get(row.memberNo.trim())
					: exist.byAnyName.get(row.memberName.trim());
			if (matched != null) {
				row.exists = true;
				// 以数据库为准：导入名若匹配到的是备用名称(别名)，预览展示的成员名称修正为数据库中的真实主名称
				row.memberName = matched.getMemberName();
			}
			else {
				row.exists = false;
			}
		}
		// 编号为空且同名成员≥2条：无法唯一匹配，提示补充编号
		for (ClanMemberRow row : rows) {
			if ((row.memberNo == null || row.memberNo.trim().isEmpty())
					&& hasDuplicateName(nameCount, row.memberName)) {
				row.error = "存在同名成员【" + row.memberName + "】，请补充编号";
			}
		}
		}
		else {
			// 联赛成员战绩 / 联赛报名数据：根据本群组内「部落成员表还没有」的人生成预览
			if (leagueNo == null || leagueNo.trim().isEmpty()) {
				return ApiResponse.error("请选择联赛");
			}
			rows = parseFromLeague(type, leagueNo.trim(), clanNo.trim(), g);
		}

		Map<String, Object> data = new HashMap<>(4);
		data.put("records", rows);
		data.put("clanNo", clanNo);
		data.put("groupNo", g);
		return ApiResponse.ok(data);
	}

	/**
	 * 根据联赛成员战绩 / 联赛报名数据生成待导入预览：查询联赛对应表（league_no + clan_no）中、
	 * 在部落成员表中尚不存在的成员（按成员名称查重，含别名）。
	 */
	private List<ClanMemberRow> parseFromLeague(String type, String leagueNo, String clanNo, String groupNo) {
		// 1) 取联赛成员（战绩表 / 报名表）
		List<LeagueMemberView> source = new ArrayList<>();
		if ("leagueRecord".equalsIgnoreCase(type)) {
			QueryWrapper<LeagueRecord> qw = new QueryWrapper<>();
			qw.eq("league_no", leagueNo).eq("clan_no", clanNo);
			qw.select("member_name", "member_no");
			List<LeagueRecord> list = leagueRecordMapper.selectList(qw);
			for (LeagueRecord r : list) {
				source.add(new LeagueMemberView(r.getMemberName(), r.getMemberNo()));
			}
		}
		else {
			QueryWrapper<LeagueSignup> qw = new QueryWrapper<>();
			qw.eq("league_no", leagueNo).eq("clan_no", clanNo);
			qw.select("member_name", "member_no");
			List<LeagueSignup> list = leagueSignupMapper.selectList(qw);
			for (LeagueSignup r : list) {
				source.add(new LeagueMemberView(r.getMemberName(), r.getMemberNo()));
			}
		}

		// 2) 过滤：部落成员表中尚不存在的人（按成员名称查重，含别名；整个群组范围，不限制同一部落）
		ExistSets exist = loadExist(groupNo);
		Map<String, Long> nameCount = countByNameMap(groupNo);
		List<ClanMemberRow> rows = new ArrayList<>();
		Set<String> seenInLeague = new LinkedHashSet<>();
		for (LeagueMemberView v : source) {
			if (v.name == null || v.name.trim().isEmpty()) {
				continue;
			}
			String name = v.name.trim();
			if (seenInLeague.contains(name)) {
				continue; // 联赛表里同名去重，避免重复预览
			}
			seenInLeague.add(name);
			boolean hasNo = v.no != null && !v.no.trim().isEmpty();
			boolean already = hasNo ? exist.nos.contains(v.no.trim()) : exist.byAnyName.containsKey(name);
			if (already) {
				continue; // 部落成员表已存在，跳过
			}
			ClanMemberRow row = new ClanMemberRow();
			row.memberName = name;
			row.memberNo = hasNo ? v.no.trim() : "";
			row.exists = false;
			// 编号为空且同名成员≥2条：无法唯一匹配，提示补充编号
			if (!hasNo && hasDuplicateName(nameCount, name)) {
				row.error = "存在同名成员【" + name + "】，请补充编号";
			}
			rows.add(row);
		}
		return rows;
	}

	/** 联赛成员（战绩/报名）的名称与编号视图。 */
	private static class LeagueMemberView {

		final String name;

		final String no;

		LeagueMemberView(String name, String no) {
			this.name = name;
			this.no = no;
		}

	}

	/** 确认导入：跳过已存在的成员（按成员名称查重），其余插入；已存在成员的导入字段（大本等级/匹配值/战斗力）非空时更新到数据库。 */
	@PostMapping("/confirm")
	public ApiResponse confirm(@RequestBody Map<String, Object> payload) {
		String clanNo = (String) payload.get("clanNo");
		String groupNo = (String) payload.get("groupNo");
		if (clanNo == null || clanNo.trim().isEmpty()) {
			return ApiResponse.error("缺少部落信息");
		}
		String g = resolveGroupNo(groupNo);
		if (g == null || g.trim().isEmpty()) {
			return ApiResponse.error("无法获取群组信息，请确认登录状态");
		}

		Object recordsObj = payload.get("records");
		if (!(recordsObj instanceof List)) {
			return ApiResponse.error("无导入数据");
		}
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> records = (List<Map<String, Object>>) recordsObj;

		ExistSets exist = loadExist(g);
		Map<String, Long> nameCount = countByNameMap(g);
		Set<String> generatedNos = new HashSet<String>();
		int inserted = 0;
		int skipped = 0;
		int updated = 0;

		for (Map<String, Object> rec : records) {
			String name = rec.get("memberName") == null ? null : String.valueOf(rec.get("memberName")).trim();
			if (name == null || name.isEmpty()) {
				skipped++;
				continue;
			}
			String no = rec.get("memberNo") == null ? null : String.valueOf(rec.get("memberNo")).trim();
			if (no != null && no.isEmpty()) {
				no = null;
			}
			// 编号为空且同名成员≥2条：无法唯一匹配，提示补充编号后重试
			if (no == null && hasDuplicateName(nameCount, name)) {
				return ApiResponse.error("存在同名成员【" + name + "】，请补充编号");
			}
			// 成员编号为空时自动生成 10 位（数字+小写字母）编号，保证整个群组下唯一（不限制同一部落）
			if (no == null) {
				no = MemberNoGenerator.generateUniqueMemberNo(clanMemberMapper, g, generatedNos);
				generatedNos.add(no);
			}
			boolean hasNo = no != null;
			// 条件唯一：填了编号 → 校验编号唯一；没填编号 → 按名称或备用名称(别名)匹配同一人
			boolean dup = hasNo ? exist.nos.contains(no) : exist.byAnyName.containsKey(name);
			if (dup) {
				// 已存在成员：若导入的大本等级/匹配值/战斗力非空，则更新这些字段
				ClanMember existMember = hasNo ? exist.byNo.get(no) : exist.byAnyName.get(name);
				Integer thLevel = toInteger(rec.get("thLevel"));
				Integer matchValue = toInteger(rec.get("matchValue"));
				Integer combatPower = toInteger(rec.get("combatPower"));
				if (existMember != null && (thLevel != null || matchValue != null || combatPower != null)) {
					ClanMember toUpdate = new ClanMember();
					toUpdate.setId(existMember.getId());
					if (thLevel != null)
						toUpdate.setThLevel(thLevel);
					if (matchValue != null)
						toUpdate.setMatchValue(matchValue);
					if (combatPower != null)
						toUpdate.setCombatPower(combatPower);
					clanMemberMapper.updateById(toUpdate);
					updated++;
				}
				else {
					skipped++;
				}
				continue;
			}
			ClanMember member = new ClanMember();
			member.setGroupNo(g);
			member.setClanNo(clanNo);
			member.setMemberName(name);
			member.setMemberNo(no);
			member.setWarStatus(0);
			// 大本等级 / 匹配值 / 战斗力：允许为空（空则使用 DB 默认 0）
			Integer thLevel = toInteger(rec.get("thLevel"));
			Integer matchValue = toInteger(rec.get("matchValue"));
			Integer combatPower = toInteger(rec.get("combatPower"));
			if (thLevel != null)
				member.setThLevel(thLevel);
			if (matchValue != null)
				member.setMatchValue(matchValue);
			if (combatPower != null)
				member.setCombatPower(combatPower);
			clanMemberMapper.insert(member);
			// 同步联赛成员战绩表 / 联赛报名表：将同名且 member_no 为空的关联记录补全为该成员编号（整个群组范围，不限制同一部落）
			syncLeagueNoByMemberName(name, g, no);
			// 把新插入的名称/编号加入已存在集合，避免同一批次内重复插入
			if (hasNo) {
				exist.nos.add(no);
			}
			else {
				exist.names.add(name);
				exist.byAnyName.put(name, member);
			}
			inserted++;
		}

		Map<String, Object> data = new HashMap<>(4);
		data.put("inserted", inserted);
		data.put("updated", updated);
		data.put("skipped", skipped);
		data.put("total", records.size());
		return ApiResponse.ok(data);
	}

	/** 下载导入模板（名称、编号两列）。 */
	@GetMapping("/template")
	public void template(HttpServletResponse response) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (Workbook wb = new XSSFWorkbook()) {
			Sheet sheet = wb.createSheet("部落成员");
			Row header = sheet.createRow(0);
			header.createCell(0).setCellValue("名称");
			header.createCell(1).setCellValue("编号");
			header.createCell(2).setCellValue("大本等级");
			header.createCell(3).setCellValue("匹配值");
			header.createCell(4).setCellValue("战斗力");
			// 示例行（编号、大本等级、匹配值、战斗力均可选填空）
			Row sample = sheet.createRow(1);
			sample.createCell(0).setCellValue("张三");
			sample.createCell(1).setCellValue("");
			sample.createCell(2).setCellValue("");
			sample.createCell(3).setCellValue("");
			sample.createCell(4).setCellValue("");
			sheet.autoSizeColumn(0);
			sheet.autoSizeColumn(1);
			sheet.autoSizeColumn(2);
			sheet.autoSizeColumn(3);
			sheet.autoSizeColumn(4);
			wb.write(out);
		}

		byte[] bytes = out.toByteArray();
		String fileName = URLEncoder.encode("部落成员导入模板.xlsx", "UTF-8").replace("+", "%20");
		response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName);
		response.setContentLength(bytes.length);
		ServletOutputStream os = response.getOutputStream();
		os.write(bytes);
		os.flush();
	}

	// ---- 内部工具 ----

	private List<ClanMemberRow> parseExcel(InputStream in) throws IOException {
		List<ClanMemberRow> rows = new ArrayList<>();
		try (Workbook wb = new XSSFWorkbook(in)) {
			Sheet sheet = wb.getSheetAt(0);
			if (sheet == null) {
				return rows;
			}
			boolean first = true;
			for (Row row : sheet) {
				if (first) {
					first = false;
					// 跳过表头：若首列是“名称”则视为表头
					String c0 = cellString(row, 0);
					if ("名称".equals(c0) || "编号".equals(cellString(row, 1))) {
						continue;
					}
				}
				String name = cellString(row, 0);
				if (name == null || name.trim().isEmpty()) {
					continue; // 跳过空行
				}
				ClanMemberRow r = new ClanMemberRow();
				r.memberName = name.trim();
				String no = cellString(row, 1);
				r.memberNo = (no == null || no.trim().isEmpty()) ? "" : no.trim();
				// 第 3/4/5 列：大本等级 / 匹配值 / 战斗力，允许为空（解析为 null）
				r.thLevel = cellInteger(row, 2);
				r.matchValue = cellInteger(row, 3);
				r.combatPower = cellInteger(row, 4);
				r.exists = false;
				rows.add(r);
			}
		}
		return rows;
	}

	/** 读取单元格整数值；空值/非数字返回 null。 */
	private Integer cellInteger(Row row, int idx) {
		if (row == null) {
			return null;
		}
		org.apache.poi.ss.usermodel.Cell cell = row.getCell(idx);
		if (cell == null) {
			return null;
		}
		switch (cell.getCellType()) {
			case STRING:
				String s = cell.getStringCellValue();
				if (s == null || s.trim().isEmpty()) {
					return null;
				}
				try {
					return Integer.valueOf(s.trim());
				}
				catch (NumberFormatException e) {
					return null;
				}
			case NUMERIC:
				return (int) cell.getNumericCellValue();
			default:
				return null;
		}
	}

	private String cellString(Row row, int idx) {
		if (row == null) {
			return null;
		}
		org.apache.poi.ss.usermodel.Cell cell = row.getCell(idx);
		if (cell == null) {
			return null;
		}
		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue();
			case NUMERIC:
				// 编号若为整数则去掉小数
				double d = cell.getNumericCellValue();
				if (d == Math.rint(d)) {
					return String.valueOf((long) d);
				}
				return String.valueOf(d);
			case BOOLEAN:
				return String.valueOf(cell.getBooleanCellValue());
			default:
				return null;
		}
	}

	/** 将对象转为 Integer；null / 空字符串 / 非数字返回 null。 */
	private Integer toInteger(Object v) {
		if (v == null) {
			return null;
		}
		if (v instanceof Number) {
			return ((Number) v).intValue();
		}
		String s = String.valueOf(v).trim();
		if (s.isEmpty()) {
			return null;
		}
		try {
			return Integer.valueOf(s);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private String resolveGroupNo(String groupNo) {
		String g = (groupNo == null || groupNo.trim().isEmpty()) ? UserContext.getGroupNo() : groupNo;
		if (g == null || g.trim().isEmpty()) {
			return null;
		}
		return g.trim();
	}

	private ExistSets loadExist(String groupNo) {
		QueryWrapper<ClanMember> qw = new QueryWrapper<>();
		qw.eq("group_no", groupNo);
		qw.select("id", "member_name", "member_no", "th_level", "match_value", "combat_power",
				"backup_name1", "backup_name2", "backup_name3", "backup_name4", "backup_name5");
		List<ClanMember> list = clanMemberMapper.selectList(qw);
		ExistSets s = new ExistSets();
		if (list != null) {
			for (ClanMember m : list) {
				if (m.getMemberName() != null) {
					String nm = m.getMemberName().trim();
					s.names.add(nm);
					s.byName.put(nm, m);
					s.byAnyName.put(nm, m);
				}
				if (m.getMemberNo() != null && !m.getMemberNo().trim().isEmpty()) {
					String no = m.getMemberNo().trim();
					s.nos.add(no);
					s.byNo.put(no, m);
				}
				addAlias(s, m.getBackupName1(), m);
				addAlias(s, m.getBackupName2(), m);
				addAlias(s, m.getBackupName3(), m);
				addAlias(s, m.getBackupName4(), m);
				addAlias(s, m.getBackupName5(), m);
			}
		}
		return s;
	}

	/** 把某个备用名称(别名)加入按任意名称匹配的映射；空值或空串忽略 */
	private static void addAlias(ExistSets s, String alias, ClanMember m) {
		if (alias != null && !alias.trim().isEmpty()) {
			s.byAnyName.put(alias.trim(), m);
		}
	}

	/** 统计指定群组内各名称（主名称 + 全部备用名称）对应的出现次数，用于导入同名去重校验；按整个群组范围统计，不限制同一部落。 */
	private Map<String, Long> countByNameMap(String groupNo) {
		QueryWrapper<ClanMember> qw = new QueryWrapper<ClanMember>();
		if (groupNo != null && !groupNo.trim().isEmpty()) {
			qw.eq("group_no", groupNo.trim());
		}
		qw.select("member_name", "backup_name1", "backup_name2", "backup_name3", "backup_name4", "backup_name5");
		List<ClanMember> list = clanMemberMapper.selectList(qw);
		Map<String, Long> map = new HashMap<String, Long>();
		for (ClanMember m : list) {
			addNameCount(map, m.getMemberName());
			addNameCount(map, m.getBackupName1());
			addNameCount(map, m.getBackupName2());
			addNameCount(map, m.getBackupName3());
			addNameCount(map, m.getBackupName4());
			addNameCount(map, m.getBackupName5());
		}
		return map;
	}

	private static void addNameCount(Map<String, Long> map, String name) {
		if (name != null && !name.trim().isEmpty()) {
			String nm = name.trim();
			map.put(nm, map.getOrDefault(nm, 0L) + 1);
		}
	}

	/** 导入校验：编号为空时，若同名（含备用名称）成员达到两条以上则无法唯一匹配。 */
	private boolean hasDuplicateName(Map<String, Long> nameCount, String name) {
		if (name == null || name.trim().isEmpty()) {
			return false;
		}
		return nameCount.getOrDefault(name.trim(), 0L) >= 2;
	}

	/** 按成员名称（整个群组范围，不限制同一部落）将联赛表中 member_no 为空的关联记录补全为 newNo。 */
	private void syncLeagueNoByMemberName(String memberName, String groupNo, String newNo) {
		if (memberName == null || memberName.trim().isEmpty()) {
			return;
		}
		String name = memberName.trim();
		QueryWrapper<LeagueRecord> rqw = new QueryWrapper<LeagueRecord>();
		if (groupNo != null && !groupNo.trim().isEmpty()) {
			rqw.eq("group_no", groupNo);
		}
		rqw.eq("member_name", name);
		rqw.and(w -> w.isNull("member_no").or().eq("member_no", ""));
		LeagueRecord ru = new LeagueRecord();
		ru.setMemberNo(newNo);
		leagueRecordMapper.update(ru, rqw);

		QueryWrapper<LeagueSignup> sqw = new QueryWrapper<LeagueSignup>();
		if (groupNo != null && !groupNo.trim().isEmpty()) {
			sqw.eq("group_no", groupNo);
		}
		sqw.eq("member_name", name);
		sqw.and(w -> w.isNull("member_no").or().eq("member_no", ""));
		LeagueSignup su = new LeagueSignup();
		su.setMemberNo(newNo);
		leagueSignupMapper.update(su, sqw);
	}

	/** 已存在成员的名称/编号集合（按整个群组 group_no 范围，不限制同一部落）。 */
	private static class ExistSets {

		Set<String> names = new LinkedHashSet<>();

		Set<String> nos = new LinkedHashSet<>();

		Map<String, ClanMember> byName = new HashMap<>();

		Map<String, ClanMember> byNo = new HashMap<>();

		/** 任意名称（主名称 + 全部备用名称）trim 后 → 成员，用于按别名匹配同一人 */
		Map<String, ClanMember> byAnyName = new HashMap<>();

	}

	/** 解析后的成员行（excel 预览用）。 */
	public static class ClanMemberRow {

		public String memberName;

		public String memberNo;

		public Integer thLevel;

		public Integer matchValue;

		public Integer combatPower;

		public boolean exists;

		/** 校验错误信息（如存在同名成员需补充编号），前端展示用 */
		public String error;

	}

}
