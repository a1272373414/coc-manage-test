package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.AuthUser;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.entity.biz.LeagueRecord;
import com.tencent.wxcloudrun.entity.biz.LeagueSignup;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
import com.tencent.wxcloudrun.mapper.LeagueRecordMapper;
import com.tencent.wxcloudrun.mapper.LeagueSignupMapper;
import com.tencent.wxcloudrun.service.LeagueImageOcrManager;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 联赛战绩导入。支持图片 / Excel / JSON 三种方式。
 *
 * - 图片：综合调用百度/腾讯 OCR 表格识别（LeagueImageOcrManager），返回数据后走共享解析。 - Excel：直接读取后走共享解析。 -
 * JSON：前端本地解析（兼容脚本输出的 metadata/data/records 格式）。
 *
 * 共享解析逻辑移植自 image_to_excel_oneclick.py： 表头关键字映射 + 内容模式检测 + attacks 规范化(777→7/7) + 跨列补救 +
 * 缺失字段推断。
 */
@RestController
@RequestMapping("/api/league/record/import")
public class LeagueImportController {

	private static final Logger log = LoggerFactory.getLogger(LeagueImportController.class);

	// ==================== 联赛战绩字段值范围（COC 官方规则） ====================
	/** 胜利之星单场最大 7，联赛累计最大 21（7×3） */
	private static final int STARS_MIN = 0;

	private static final int STARS_MAX = 21;
	/** 摧毁率单次 0-100，联赛累计最大 700（7×100） */
	private static final int DEST_MIN = 0;

	private static final int DEST_MAX = 700;
	/** 进攻次数单场 0-7 */
	private static final int ATK_MIN = 0;

	private static final int ATK_MAX = 7;
	/** 联赛排名 1-50（50 名成员上限） */
	private static final int RANK_MIN = 1;

	private static final int RANK_MAX = 50;

	@Resource
	private LeagueRecordMapper leagueRecordMapper;

	@Resource
	private ClanMemberMapper clanMemberMapper;

	@Resource
	private LeagueSignupMapper leagueSignupMapper;

	@Resource
	private LeagueImageOcrManager imageOcrManager;

	/**
	 * 预览：接收图片或 Excel 文件，解析后返回预览数据。 JSON 方式由前端本地解析，不走此接口。
	 */
	@PostMapping("/preview")
	public ApiResponse preview(@RequestParam("type") String type,
			@RequestParam(value = "leagueNo", required = false) String leagueNo,
			@RequestParam(value = "clanNo", required = false) String clanNo,
			@RequestParam(value = "groupNo", required = false) String groupNo,
			@RequestParam(value = "files", required = false) MultipartFile[] files) throws Exception {
		AuthUser user = UserContext.get();
		if (user == null)
			return ApiResponse.error("请先登录");

		List<Map<String, Object>> list = new ArrayList<>();
		if ("excel".equalsIgnoreCase(type)) {
			if (files == null || files.length == 0)
				return ApiResponse.error("请上传 Excel 文件");
			list = parseExcel(files[0], leagueNo, clanNo, groupNo);
		}
		else if ("image".equalsIgnoreCase(type)) {
			if (files == null || files.length == 0)
				return ApiResponse.error("请上传图片");
			list = parseImages(files, leagueNo, clanNo, groupNo);
		}
		else {
			return ApiResponse.error("暂不支持该导入方式");
		}

		// 标记成员是否存在于部落成员表（按 名称 + 部落编号 + 群组编号 匹配）
		markMemberExists(list, clanNo, groupNo);

		Map<String, Object> result = new HashMap<>();
		result.put("records", list);
		result.put("total", list.size());
		result.put("type", type);
		return ApiResponse.ok(result);
	}

	/**
	 * 成员存在性批量查询（JSON 导入前端本地解析后调用，补 memberExists 标识）。 请求体：{ clanNo, groupNo, names:
	 * ["名称1","名称2",...] } 返回：{ exists: ["存在的名称", ...] }
	 */
	@PostMapping("/checkMembers")
	public ApiResponse checkMembers(@RequestBody Map<String, Object> body) {
		AuthUser user = UserContext.get();
		if (user == null)
			return ApiResponse.error("请先登录");
		String clanNo = asString(body.get("clanNo"));
		String groupNo = asString(body.get("groupNo"));
		Object namesObj = body.get("names");
		if (!(namesObj instanceof List))
			return ApiResponse.error("names 参数错误");
		@SuppressWarnings("unchecked")
		List<Object> names = (List<Object>) namesObj;

		Set<String> exists = new HashSet<>();
		Map<String, String> nameMap = new HashMap<>();
		if (clanNo != null && !clanNo.isEmpty() && groupNo != null && !groupNo.isEmpty() && !names.isEmpty()) {
			Map<String, String> all = loadClanMemberNameMap(clanNo, groupNo);
			for (Object o : names) {
				String n = asString(o);
				if (n != null && !n.isEmpty() && all.containsKey(n.trim()))
					exists.add(n.trim());
			}
			nameMap = all;
		}
		Map<String, Object> result = new HashMap<>();
		result.put("exists", exists);
		// 返回名称映射（别名 → 数据库真实名称），供前端修正预览界面成员名称
		result.put("nameMap", nameMap);
		return ApiResponse.ok(result);
	}

	/** 为解析结果中的每条记录标记 memberExists：该成员是否存在于所选部落+群组的成员表中（含备用名称匹配） */
	private void markMemberExists(List<Map<String, Object>> list, String clanNo, String groupNo) {
		Map<String, String> nameMap = new HashMap<>();
		if (clanNo != null && !clanNo.isEmpty() && groupNo != null && !groupNo.isEmpty()) {
			nameMap = loadClanMemberNameMap(clanNo, groupNo);
		}
		for (Map<String, Object> m : list) {
			String nm = asString(m.get("memberName"));
			if (nm != null && !nm.trim().isEmpty() && nameMap.containsKey(nm.trim())) {
				m.put("memberExists", true);
				// 以数据库为准：导入名若匹配到的是备用名称(别名)，预览展示的成员名称修正为数据库中的真实主名称
				m.put("memberName", nameMap.get(nm.trim()));
			} else {
				m.put("memberExists", false);
			}
		}
	}

	/**
	 * 一次性查出该部落+群组下的成员名称映射（任意名称 trim → 数据库真实 member_name），用于按别名匹配同一成员。 映射键包含：主名称 + 全部备用名称。
	 */
	private Map<String, String> loadClanMemberNameMap(String clanNo, String groupNo) {
		QueryWrapper<ClanMember> qw = new QueryWrapper<>();
		qw.select("member_name", "backup_name1", "backup_name2", "backup_name3", "backup_name4", "backup_name5")
				.eq("clan_no", clanNo).eq("group_no", groupNo);
		List<ClanMember> rows = clanMemberMapper.selectList(qw);
		Map<String, String> map = new HashMap<>();
		for (ClanMember r : rows) {
			if (r.getMemberName() == null)
				continue;
			String real = r.getMemberName().trim();
			map.put(real, real);
			putAlias(map, r.getBackupName1(), real);
			putAlias(map, r.getBackupName2(), real);
			putAlias(map, r.getBackupName3(), real);
			putAlias(map, r.getBackupName4(), real);
			putAlias(map, r.getBackupName5(), real);
		}
		return map;
	}

	/** 把某个备用名称(别名)加入映射；空值或空串忽略 */
	private static void putAlias(Map<String, String> map, String alias, String real) {
		if (alias != null && !alias.trim().isEmpty()) {
			map.put(alias.trim(), real);
		}
	}

	/** 一次性查出该联赛+部落下的报名状态映射（成员名称 -> 报名状态），用于导入时同步 */
	private Map<String, Integer> loadSignupStatus(String leagueNo, String clanNo) {
		QueryWrapper<LeagueSignup> qw = new QueryWrapper<>();
		qw.select("member_name", "signup_status").eq("league_no", leagueNo).eq("clan_no", clanNo);
		List<LeagueSignup> rows = leagueSignupMapper.selectList(qw);
		Map<String, Integer> map = new HashMap<>();
		for (LeagueSignup s : rows) {
			if (s.getMemberName() != null)
				map.put(s.getMemberName().trim(), s.getSignupStatus());
		}
		return map;
	}

	/**
	 * 确认导入：批量保存到 league_record。 业务处理先简单：直接按行 insert，不做唯一性 / 重复校验，后续完善。
	 */
	@PostMapping("/confirm")
	public ApiResponse confirm(@RequestBody Map<String, Object> body) {
		AuthUser user = UserContext.get();
		if (user == null)
			return ApiResponse.error("请先登录");

		String leagueNo = asString(body.get("leagueNo"));
		String clanNo = asString(body.get("clanNo"));
		String groupNo = asString(body.get("groupNo"));
		if (leagueNo == null || leagueNo.isEmpty())
			return ApiResponse.error("请选择联赛");
		if (clanNo == null || clanNo.isEmpty())
			return ApiResponse.error("请选择部落");
		if (groupNo == null || groupNo.isEmpty())
			return ApiResponse.error("请选择群组");

		Object recObj = body.get("records");
		if (!(recObj instanceof List))
			return ApiResponse.error("无导入数据");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> records = (List<Map<String, Object>>) recObj;
		if (records.isEmpty())
			return ApiResponse.error("无导入数据");

		// 先删除同一部落同一联赛的旧数据，再插入新数据（覆盖式导入）
		QueryWrapper<LeagueRecord> deleteQw = new QueryWrapper<>();
		deleteQw.eq("league_no", leagueNo).eq("clan_no", clanNo);
		long deletedCount = leagueRecordMapper.delete(deleteQw);
		if (deletedCount > 0) {
			log.info("联赛战绩覆盖导入: league={}, clan={}, 已删除旧数据 {} 条", leagueNo, clanNo, deletedCount);
		}

		// 一次性查出该联赛+部落下的报名状态映射（成员名称 -> 报名状态）
		Map<String, Integer> signupMap = loadSignupStatus(leagueNo, clanNo);
		// 成员名称映射（主名称 + 全部备用名称 -> 真实主名称），用于按别名匹配同一成员
		Map<String, String> nameMap = loadClanMemberNameMap(clanNo, groupNo);

		int count = 0;
		for (Map<String, Object> r : records) {
			String rawName = asString(r.get("memberName"));
			String realName = (rawName != null && nameMap.containsKey(rawName.trim()))
					? nameMap.get(rawName.trim())
					: rawName;
			LeagueRecord rec = new LeagueRecord();
			rec.setMemberName(realName);
			rec.setMemberNo(asString(r.get("memberNo")));
			rec.setMemberRank(toInt(r.get("rank"), 0));
			rec.setLeagueNo(orDefault(asString(r.get("leagueNo")), leagueNo));
			rec.setClanNo(orDefault(asString(r.get("clanNo")), clanNo));
			rec.setGroupNo(orDefault(asString(r.get("groupNo")), groupNo));
			rec.setWinStars(toInt(r.get("winStars"), 0));
			rec.setDestroyRate(toInt(r.get("destroyRate"), 0));
			rec.setActualAttacks(toInt(r.get("actualAttacks"), 0));
			rec.setRequiredAttacks(toInt(r.get("requiredAttacks"), 0));
			rec.setHasExtra(toInt(r.get("hasExtra"), 0));
			// 报名状态：按 成员名称+联赛编号+部落编号 查报名表，命中则同步，否则默认未报名(1)
			String mName = realName;
			Integer signupStatus = (mName != null) ? signupMap.get(mName.trim()) : null;
			rec.setSignupStatus(signupStatus != null ? signupStatus : 1);
			leagueRecordMapper.insert(rec);
			count++;
		}

		Map<String, Object> result = new HashMap<>();
		result.put("inserted", count);
		result.put("deleted", (int) deletedCount);
		return ApiResponse.ok(result);
	}

	/**
	 * 下载示例文件：Excel(xlsx) 或 JSON。
	 */
	@GetMapping("/template")
	public void template(@RequestParam("type") String type, HttpServletResponse response) throws IOException {
		if ("json".equalsIgnoreCase(type)) {
			response.setContentType("application/json;charset=utf-8");
			response.setHeader("Content-Disposition", "attachment; filename=league-record-example.json");
			response.getWriter().write(EXAMPLE_JSON);
			return;
		}
		// 默认 excel
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=league-record-example.xlsx");
		Workbook wb = new XSSFWorkbook();
		Sheet sheet = wb.createSheet("联赛战绩");
		Row header = sheet.createRow(0);
		String[] headers = { "排名", "名称", "胜利之星", "摧毁率", "进攻(如7/7)" };
		for (int i = 0; i < headers.length; i++) {
			header.createCell(i).setCellValue(headers[i]);
		}
		Row r1 = sheet.createRow(1);
		r1.createCell(0).setCellValue(1);
		r1.createCell(1).setCellValue("示例成员");
		r1.createCell(2).setCellValue(21);
		r1.createCell(3).setCellValue(700.0);
		r1.createCell(4).setCellValue("7/7");
		for (int i = 0; i < headers.length; i++)
			sheet.autoSizeColumn(i);
		wb.write(response.getOutputStream());
		wb.close();
	}

	// ==================== 解析逻辑（图片走腾讯云OCR，Excel/OCR 共用表格行解析） ====================

	/** Excel 解析：读取第一个 sheet 的所有行，交给共享解析器 */
	private List<Map<String, Object>> parseExcel(MultipartFile file, String leagueNo, String clanNo, String groupNo)
			throws Exception {
		List<String[]> rows = readExcelRows(file);
		return parseTableRows(rows, leagueNo, clanNo, groupNo);
	}

	/** 图片解析：按文件名排序，逐张调用腾讯云OCR表格识别，识别失败的图生成一行空模板由用户补全 */
	private List<Map<String, Object>> parseImages(MultipartFile[] files, String leagueNo, String clanNo,
			String groupNo) {
		List<MultipartFile> sorted = new ArrayList<>(Arrays.asList(files));
		sorted.sort(Comparator.comparing(f -> f.getOriginalFilename() == null ? "" : f.getOriginalFilename()));
		List<Map<String, Object>> list = new ArrayList<>();
		int fallbackIdx = 1;
		for (MultipartFile f : sorted) {
			String fn = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
			log.info("联赛战绩导入：开始处理图片 {}，大小 {} bytes", fn, f.getSize());
			try {
				List<String[]> rows = imageOcrManager.ocrToRows(f);
				int rowCnt = (rows == null) ? 0 : rows.size();
				log.info("联赛战绩导入：{} OCR 综合返回 {} 行", fn, rowCnt);
				if (rows != null && !rows.isEmpty()) {
					List<Map<String, Object>> parsed = parseTableRows(rows, leagueNo, clanNo, groupNo);
					int parsedCnt = (parsed == null) ? 0 : parsed.size();
					log.info("联赛战绩导入：{} 解析得到 {} 条有效记录", fn, parsedCnt);
					if (parsed != null && !parsed.isEmpty()) {
						list.addAll(parsed);
						continue;
					}
					// OCR 返回了行，但解析后没有可用数据行（如列识别失败导致全部行被 continue）
					Map<String, Object> m = newEmptyRow(leagueNo, clanNo, groupNo);
					m.put("rank", String.valueOf(fallbackIdx++));
					m.put("remark", fn + " (OCR识别到 " + rows.size() + " 行但解析无结果，请手动补全)");
					list.add(m);
					continue;
				}
			}
			catch (Exception e) {
				// OCR 异常：留一行空模板，标注失败原因，便于用户手动补全
				log.error("联赛战绩导入：{} OCR 调用失败", fn, e);
				Map<String, Object> m = newEmptyRow(leagueNo, clanNo, groupNo);
				m.put("rank", String.valueOf(fallbackIdx++));
				String msg = e.getMessage();
				if (msg != null && msg.length() > 100)
					msg = msg.substring(0, 100);
				m.put("remark", fn + " (识别失败: " + (msg == null ? "未知错误" : msg) + ")");
				list.add(m);
				continue;
			}
			// OCR 返回空 rows
			log.warn("联赛战绩导入：{} OCR 返回空数据", fn);
			Map<String, Object> m = newEmptyRow(leagueNo, clanNo, groupNo);
			m.put("rank", String.valueOf(fallbackIdx++));
			m.put("remark", fn + " (识别失败，请手动补全)");
			list.add(m);
		}
		return list;
	}

	private Map<String, Object> newEmptyRow(String leagueNo, String clanNo, String groupNo) {
		Map<String, Object> m = new HashMap<>();
		m.put("rank", "");
		m.put("memberName", "");
		m.put("winStars", 0);
		m.put("destroyRate", 0);
		m.put("actualAttacks", 0);
		m.put("requiredAttacks", 0);
		m.put("hasExtra", 0);
		m.put("signupStatus", null);
		m.put("leagueNo", leagueNo);
		m.put("clanNo", clanNo);
		m.put("groupNo", groupNo);
		return m;
	}

	/** 读取上传 Excel 第一个 sheet 的所有非空行，每行转为字符串数组 */
	private List<String[]> readExcelRows(MultipartFile file) throws Exception {
		List<String[]> rows = new ArrayList<>();
		Workbook wb = WorkbookFactory.create(file.getInputStream());
		try {
			Sheet sheet = wb.getSheetAt(0);
			for (int r = 0; r <= sheet.getLastRowNum(); r++) {
				Row row = sheet.getRow(r);
				if (row == null)
					continue;
				int last = row.getLastCellNum();
				List<String> cells = new ArrayList<>();
				boolean any = false;
				for (int c = 0; c < last; c++) {
					String v = cellToStr(row.getCell(c));
					cells.add(v);
					if (v != null && !v.isEmpty())
						any = true;
				}
				if (any)
					rows.add(cells.toArray(new String[0]));
			}
		}
		finally {
			wb.close();
		}
		return rows;
	}

	private String cellToStr(Cell cell) {
		if (cell == null)
			return "";
		try {
			switch (cell.getCellType()) {
				case STRING:
					return cell.getStringCellValue().trim();
			case NUMERIC:
				if (DateUtil.isCellDateFormatted(cell)) {
					// 进攻次数形如 "7/7" 被 Excel 自动识别为日期（如 2026/7/7），还原为 "月/日"
					Date dt = cell.getDateCellValue();
					Calendar cal = Calendar.getInstance();
					cal.setTime(dt);
					return (cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.DAY_OF_MONTH);
				}
				double d = cell.getNumericCellValue();
					if (d == Math.floor(d))
						return String.valueOf((long) d);
					return String.valueOf(d);
				case BOOLEAN:
					return String.valueOf(cell.getBooleanCellValue());
				case FORMULA:
					return cell.getStringCellValue();
				default:
					return "";
			}
		}
		catch (Exception e) {
			return "";
		}
	}

	/**
	 * 共享表格行解析（移植自 Python 版 image_to_excel_oneclick.py）。 流程：表头关键字映射 → 不完整时按内容模式补全列 → 逐行取字段
	 * + 跨列补救 → attacks 规范化(777→7/7) → 缺失字段推断(未参战补0 / 排名连续性)。
	 */
	private List<Map<String, Object>> parseTableRows(List<String[]> rows, String leagueNo, String clanNo,
			String groupNo) {
		List<Map<String, Object>> result = new ArrayList<>();
		if (rows == null || rows.isEmpty())
			return result;

		String[] header = rows.get(0);
		boolean isHeader = isHeaderRow(header);
		List<String[]> dataRows = isHeader ? new ArrayList<>(rows.subList(1, rows.size())) : rows;
		if (dataRows.isEmpty())
			return result;

		Map<String, Integer> colMap = isHeader ? mapColumns(header) : new HashMap<>();
		if (colMap == null)
			colMap = new HashMap<>();
		// 表头映射不全时，用内容模式检测补充缺失字段
		if (colMap.size() < 5) {
			Map<String, Integer> pattern = detectColumnsByPattern(dataRows);
			if (pattern != null) {
				Set<Integer> used = new HashSet<>(colMap.values());
				for (String role : new String[] { "attacks", "name", "destruction", "rank", "stars" }) {
					Integer pi = pattern.get(role);
					if (!colMap.containsKey(role) && pi != null && !used.contains(pi)) {
						colMap.put(role, pi);
						used.add(pi);
					}
				}
			}
		}

		for (String[] cells : dataRows) {
			Integer rankIdx = colMap.get("rank");
			Integer nameIdx = colMap.get("name");
			Integer starsIdx = colMap.get("stars");
			Integer destIdx = colMap.get("destruction");
			Integer atkIdx = colMap.get("attacks");
			Integer actualIdx = colMap.get("actual");
			Integer requiredIdx = colMap.get("required");

			String rank = rankIdx != null ? safeGet(cells, rankIdx) : null;
			// 规范化排名：去除尾部点/空格（处理 OCR 误读如 "1." "29." → "1" "29"）
			if (rank != null) {
				rank = rank.replaceAll("[\\.\\s]+$", "");
				if (rank.isEmpty())
					rank = null;
			}
			String name = nameIdx != null ? safeGet(cells, nameIdx) : null;
			String stars = starsIdx != null ? safeGet(cells, starsIdx) : null;
			String dest = destIdx != null ? safeGet(cells, destIdx) : null;
			String atk = atkIdx != null ? safeGet(cells, atkIdx) : null;

// 跨列补救：某字段为空时，扫描所有列找匹配内容（严格按值范围匹配）
		if (atk == null) {
			for (int ci = 0; ci < cells.length; ci++) {
				String c = cells[ci];
				// 跳过已被识别为其它字段的列，避免误把其它字段数字当进攻次数
				if (rankIdx != null && ci == rankIdx)
					continue;
				if (nameIdx != null && ci == nameIdx)
					continue;
				if (starsIdx != null && ci == starsIdx)
					continue;
				if (destIdx != null && ci == destIdx)
					continue;
				if (actualIdx != null && ci == actualIdx)
					continue;
				if (requiredIdx != null && ci == requiredIdx)
					continue;
				// 进攻次数格式 X/Y 且 X、Y 都在 0-7 范围
				Matcher mAtk = Pattern.compile("^(\\d+)\\s*/\\s*(\\d+)$").matcher(c == null ? "" : c);
				if (mAtk.matches()) {
					int a = Integer.parseInt(mAtk.group(1));
					int r = Integer.parseInt(mAtk.group(2));
					if (a >= ATK_MIN && a <= ATK_MAX && r >= ATK_MIN && r <= ATK_MAX) {
						atk = a + "/" + r;
						break;
					}
				}
			}
		}
		if (dest == null) {
			for (int ci = 0; ci < cells.length; ci++) {
				String c = cells[ci];
				// 跳过已被识别为其它字段的列，避免把排名数字（如"33"）误填到摧毁率
				if (rankIdx != null && ci == rankIdx)
					continue;
				if (nameIdx != null && ci == nameIdx)
					continue;
				if (starsIdx != null && ci == starsIdx)
					continue;
				if (atkIdx != null && ci == atkIdx)
					continue;
				if (actualIdx != null && ci == actualIdx)
					continue;
				if (requiredIdx != null && ci == requiredIdx)
					continue;
				if (c != null && c.matches("^\\d{1,3}(\\.\\d+)?$")) {
					int v = Integer.parseInt(c.replaceAll("\\.\\d+$", "").trim());
					// 摧毁率 0-700 范围；同时排除明显是排名(1-50)或胜利之星(0-21)的小数字
					if (v >= DEST_MIN && v <= DEST_MAX && v > STARS_MAX) {
						dest = c.trim();
						break;
					}
				}
			}
		}
		if (stars == null) {
			for (int ci = 0; ci < cells.length; ci++) {
				String c = cells[ci];
				// 跳过已被识别为其它字段的列，避免把排名数字（如"33"）误填到胜利之星
				if (rankIdx != null && ci == rankIdx)
					continue;
				if (destIdx != null && ci == destIdx)
					continue;
				if (atkIdx != null && ci == atkIdx)
					continue;
				if (actualIdx != null && ci == actualIdx)
					continue;
				if (requiredIdx != null && ci == requiredIdx)
					continue;
				if (nameIdx != null && ci == nameIdx)
					continue;
				// 胜利之星 0-21 范围；带★后缀（"21★"）也兼容
				if (c != null) {
					Matcher ms = Pattern.compile("^(\\d{1,2})\\s*★?\\s*$").matcher(c.trim());
					if (ms.matches()) {
						int v = Integer.parseInt(ms.group(1));
						if (v >= STARS_MIN && v <= STARS_MAX) {
							stars = String.valueOf(v);
							break;
						}
					}
				}
			}
		}

			int actual, required;
			if (actualIdx != null && requiredIdx != null) {
				actual = toInt(safeGet(cells, actualIdx), 0);
				required = toInt(safeGet(cells, requiredIdx), 0);
			}
			else {
				String atkNorm = normalizeAttacks(atk);
				if (atkNorm != null)
					atk = atkNorm;
				else if (atk != null && !atk.contains("/"))
					atk = null;
				int[] at = parseAttacks(atk);
				actual = at[0];
				required = at[1];
			}

			if ((rank == null || rank.isEmpty()) && (name == null || name.isEmpty()))
				continue;

			Map<String, Object> m = new HashMap<>();
			m.put("rank", rank == null ? "" : rank);
			m.put("memberName", name == null ? "" : name);
			m.put("winStars", toInt(stars, 0));
			m.put("destroyRate", (int) Math.round(toDouble(dest)));
			m.put("attacks", atk == null ? "" : atk);
			m.put("actualAttacks", actual);
			m.put("requiredAttacks", required);
			m.put("hasExtra", 0);
			m.put("signupStatus", null);
			m.put("leagueNo", leagueNo);
			m.put("clanNo", clanNo);
			m.put("groupNo", groupNo);
			result.add(m);
		}

		inferMissingFields(result);
		return result;
	}

	/** 判断首行是否为表头（命中 >=2 个表头关键字） */
	private boolean isHeaderRow(String[] cells) {
		String[] kws = { "排名", "名称", "胜利", "摧毁", "推毁", "进攻", "rank", "name", "star", "dest", "attack" };
		StringBuilder sb = new StringBuilder();
		for (String c : cells)
			if (c != null)
				sb.append(c);
		String combined = sb.toString().toLowerCase();
		int hits = 0;
		for (String kw : kws)
			if (combined.contains(kw.toLowerCase()))
				hits++;
		return hits >= 2;
	}

	/** 根据表头文本映射列索引（contains 模糊匹配，含 OCR 误读"推毁"兼容） */
	private Map<String, Integer> mapColumns(String[] header) {
		Map<String, Integer> m = new HashMap<>();
		for (int i = 0; i < header.length; i++) {
			String cl = header[i] == null ? "" : header[i].toLowerCase();
			if (containsAny(cl, "实际进攻", "实进攻", "实际攻击"))
				m.put("actual", i);
			else if (containsAny(cl, "应进攻", "应该进攻", "应该攻击"))
				m.put("required", i);
			else if (containsAny(cl, "摧毁", "推毁", "dest", "破坏", "毁率", "%"))
				m.put("destruction", i);
			else if (containsAny(cl, "星", "胜利", "star"))
				m.put("stars", i);
			else if (containsAny(cl, "名称", "成员", "name", "玩家", "姓名", "名字"))
				m.put("name", i);
			else if (containsAny(cl, "进攻", "攻击", "attack"))
				m.put("attacks", i);
			else if (containsAny(cl, "排名", "rank", "名次", "序号", "#"))
				m.put("rank", i);
		}
		return m;
	}

	/**
	 * 无表头或表头不全时，按数据行的内容模式自动检测各列角色。 各字段值范围： 胜利之星 0-21（★图标）/ 纯数字 0-21
	 * 摧毁率 0-700 进攻次数 0-7（X/Y格式或"777"误读） 排名 1-50
	 */
	private Map<String, Integer> detectColumnsByPattern(List<String[]> dataRows) {

		if (dataRows == null || dataRows.isEmpty())
			return null;
		int nCols = 0;
		for (String[] r : dataRows)
			nCols = Math.max(nCols, r.length);
		if (nCols == 0)
			return null;

		int[] atkHits = new int[nCols];
		int[] atkRepeat = new int[nCols];
		int[] textHits = new int[nCols];
		int[] destHits = new int[nCols];
		int[] rankHits = new int[nCols];
		int[] starHits = new int[nCols];
		List<List<Integer>> numVals = new ArrayList<>();
		for (int i = 0; i < nCols; i++)
			numVals.add(new ArrayList<>());

		for (String[] row : dataRows) {
			for (int ci = 0; ci < nCols; ci++) {
				String val = safeGet(row, ci);
				if (val == null || val.trim().isEmpty())
					continue;
				String s = val.trim();

				// 进攻次数：标准 X/Y 格式，且 X、Y 都在 0-7 范围
				Matcher mAtk = Pattern.compile("^(\\d+)\\s*/\\s*(\\d+)$").matcher(s);
				if (mAtk.matches()) {
					int a = Integer.parseInt(mAtk.group(1));
					int r = Integer.parseInt(mAtk.group(2));
					if (a >= ATK_MIN && a <= ATK_MAX && r >= ATK_MIN && r <= ATK_MAX) {
						atkHits[ci]++;
						continue;
					}
				}
				// OCR 误读：7/7 → 777（3 位重复数字），仅当首数字在 0-7 范围时才视为进攻
				Matcher mr = Pattern.compile("^(\\d)\\1{1,2}$").matcher(s);
				if (mr.matches() && s.length() == 3) {
					int d = Integer.parseInt(s.substring(0, 1));
					if (d >= ATK_MIN && d <= ATK_MAX) {
						atkRepeat[ci]++;
						continue;
					}
				}

				// 含中文/英文 → 名称列 或 胜利之星带★格式
				if (s.matches(".*[\\u4e00-\\u9fa5a-zA-Z].*")) {
					Matcher ms = Pattern.compile("^(\\d{1,2})\\s*★").matcher(s);
					if (ms.matches()) {
						int num = Integer.parseInt(ms.group(1));
						if (num >= STARS_MIN && num <= STARS_MAX) {
							starHits[ci]++;
							numVals.get(ci).add(num);
						}
					}
					else {
						textHits[ci]++;
					}
					continue;
				}

				// 胜利之星列在 0★ 时，OCR 可能输出 "0★"/"0 ★"/"★0"/纯 "★"，需要兼容识别
				Matcher mstar = Pattern.compile("^\\s*(\\d{0,2})?\\s*[★☆*]\\s*(\\d{0,2})?\\s*$").matcher(s);
				if (mstar.matches()) {
					String numStr = mstar.group(1);
					if (numStr == null || numStr.isEmpty())
						numStr = mstar.group(2);
					int num = (numStr == null || numStr.isEmpty()) ? 0 : Integer.parseInt(numStr);
					if (num >= STARS_MIN && num <= STARS_MAX) {
						starHits[ci]++;
						numVals.get(ci).add(num);
					}
					continue;
				}

				// 纯数字：按值范围判定 rank(1-50) / destruction(0-700) / stars(0-21)
				Matcher mn = Pattern.compile("^(\\d+)\\.?$").matcher(s);
				if (mn.matches()) {
					int num = Integer.parseInt(mn.group(1));
					numVals.get(ci).add(num);
					if (num >= RANK_MIN && num <= RANK_MAX)
						rankHits[ci]++;
					// 摧毁率：100-700 是典型摧毁率值，最易识别
					if (num >= 100 && num <= DEST_MAX)
						destHits[ci]++;
					if (num >= STARS_MIN && num <= STARS_MAX)
						starHits[ci]++;
				}
				else {
					// 含小数点的摧毁率（如 "700.0"）
					Matcher md = Pattern.compile("^(\\d{1,3})(\\.\\d+)?$").matcher(s);
					if (md.matches()) {
						int num = Integer.parseInt(md.group(1));
						if (num >= DEST_MIN && num <= DEST_MAX) {
							destHits[ci]++;
							numVals.get(ci).add(num);
						}
					}
				}
			}
		}

		Map<String, Integer> mapping = new HashMap<>();
		Set<Integer> used = new HashSet<>();

		int[] p = pickBest(atkHits, used);
		if (p[1] > 0) {
			mapping.put("attacks", p[0]);
			used.add(p[0]);
		}
		else {
			p = pickBest(atkRepeat, used);
			if (p[1] > 0) {
				mapping.put("attacks", p[0]);
				used.add(p[0]);
			}
		}

		p = pickBest(textHits, used);
		if (p[1] > 0) {
			mapping.put("name", p[0]);
			used.add(p[0]);
		}

p = pickBest(destHits, used);
		if (p[1] > 0) {
			mapping.put("destruction", p[0]);
			used.add(p[0]);
		}

		p = pickBest(rankHits, used);
		if (p[1] > 0) {
			mapping.put("rank", p[0]);
			used.add(p[0]);
		}

		// 剩余有数值的列按均值区分 星数(0-21 均值小) / 摧毁率(0-700 均值大)
		List<Integer> remaining = new ArrayList<>();
		for (int ci = 0; ci < nCols; ci++) {
			if (!used.contains(ci) && !numVals.get(ci).isEmpty())
				remaining.add(ci);
		}
		if (!remaining.isEmpty()) {
			// 优先：均值 ≤ STARS_MAX 的列作为 stars
			if (!mapping.containsKey("stars")) {
				int starCol = -1;
				for (int ci : remaining) {
					double a = avg(numVals.get(ci));
					if (a >= STARS_MIN && a <= STARS_MAX) {
						starCol = ci;
						break;
					}
				}
				if (starCol >= 0) {
					mapping.put("stars", starCol);
					used.add(starCol);
					remaining.remove(Integer.valueOf(starCol));
				}
			}
			// 否则按均值最小原则
			if (!mapping.containsKey("stars") && !remaining.isEmpty()) {
				remaining.sort((a, b) -> Double.compare(avg(numVals.get(a)), avg(numVals.get(b))));
				mapping.put("stars", remaining.get(0));
				used.add(remaining.get(0));
				remaining = new ArrayList<>(remaining.subList(1, remaining.size()));
			}
			// 优先：均值 > STARS_MAX（即 > 21）的列作为 destruction
			if (!mapping.containsKey("destruction") && !remaining.isEmpty()) {
				int destCol = -1;
				for (int ci : remaining) {
					double a = avg(numVals.get(ci));
					if (a > STARS_MAX && a <= DEST_MAX) {
						destCol = ci;
						break;
					}
				}
				if (destCol >= 0) {
					mapping.put("destruction", destCol);
					used.add(destCol);
					remaining.remove(Integer.valueOf(destCol));
				}
				else {
					// 兜底：取均值最大的列
					int last = remaining.get(remaining.size() - 1);
					mapping.put("destruction", last);
					used.add(last);
				}
			}
		}

		// 排名补充：第一个未用的有数值列
		if (!mapping.containsKey("rank")) {
			for (int ci = 0; ci < nCols; ci++) {
				if (!used.contains(ci) && !numVals.get(ci).isEmpty()) {
					mapping.put("rank", ci);
					used.add(ci);
					break;
				}
			}
		}

		return mapping.size() >= 3 ? mapping : null;
	}

	private static double avg(List<Integer> xs) {
		if (xs.isEmpty())
			return 0;
		long s = 0;
		for (int x : xs)
			s += x;
		return (double) s / xs.size();
	}

	/** 从未使用列中选得分最高的，返回 [colIndex, score] */
	private int[] pickBest(int[] scores, Set<Integer> used) {
		int bestCi = -1, bestScore = 0;
		for (int ci = 0; ci < scores.length; ci++) {
			if (!used.contains(ci) && scores[ci] > bestScore) {
				bestScore = scores[ci];
				bestCi = ci;
			}
		}
		return new int[] { bestCi, bestScore };
	}

	/**
	 * 规范化进攻次数：标准 X/Y 或 OCR 误读(777→7/7, 000→0/0)。无法识别返回 null。 仅将 3 位重复数字（如 777）视为进攻误读，2 位重复（如 33、44）通常是排名，避免误判。
	 * 同时限制 X、Y 数字在 ATK_MIN(0)~ATK_MAX(7) 范围内。
	 */
	private String normalizeAttacks(String val) {
		if (val == null)
			return null;
		String s = val.trim();
		Matcher m = Pattern.compile("^(\\d+)\\s*/\\s*(\\d+)$").matcher(s);
		if (m.matches()) {
			int a = Integer.parseInt(m.group(1));
			int r = Integer.parseInt(m.group(2));
			if (a >= ATK_MIN && a <= ATK_MAX && r >= ATK_MIN && r <= ATK_MAX)
				return a + "/" + r;
		}
		m = Pattern.compile("^(\\d)\\1{1,2}$").matcher(s);
		if (m.matches() && s.length() == 3) {
			int d = Integer.parseInt(s.substring(0, 1));
			if (d >= ATK_MIN && d <= ATK_MAX)
				return d + "/" + d;
		}
		return null;
	}

	/**
	 * 缺失字段推断（移植自 Python _infer_missing_fields）： 1. 未参战行(0/0 或 0/1) → 胜利之星/摧毁率补0 2. 第一行无排名
	 * → 推断为1 3. 排名连续性：前后排名差2时填补中间值
	 */
	private void inferMissingFields(List<Map<String, Object>> data) {
		if (data == null || data.isEmpty())
			return;
		for (int i = 0; i < data.size(); i++) {
			Map<String, Object> row = data.get(i);
			String atkStr = asString(row.get("attacks"));
			// 仅当进攻次数字符串明确为 0/0 或 0/1 时才视为未参战。OCR 漏识别胜利之星/进攻时 atkStr=null，
			// 此时 actual/required=0/0 不能视为未参战，否则会把整行 winStars/destroyRate 误清 0。
			boolean notParticipated = "0/0".equals(atkStr) || "0/1".equals(atkStr);
			if (notParticipated) {
				row.put("winStars", 0);
				row.put("destroyRate", 0);
			}
			String rank = asString(row.get("rank"));
			if ((rank == null || rank.isEmpty()) && i == 0) {
				row.put("rank", "1");
			}
		}
		for (int i = 1; i < data.size() - 1; i++) {
			String r = asString(data.get(i).get("rank"));
			if (r == null || r.isEmpty()) {
				Integer prev = parseIntOrNull(asString(data.get(i - 1).get("rank")));
				Integer next = parseIntOrNull(asString(data.get(i + 1).get("rank")));
				if (prev != null && next != null && next - prev == 2) {
					data.get(i).put("rank", String.valueOf(prev + 1));
				}
			}
		}
	}

	// ==================== 工具方法 ====================

	private String safeGet(String[] cells, int idx) {
		if (idx < 0 || idx >= cells.length)
			return null;
		String v = cells[idx];
		return (v == null || v.isEmpty()) ? null : v.trim();
	}

	private boolean containsAny(String header, String... keys) {
		for (String k : keys) {
			if (header.contains(k))
				return true;
		}
		return false;
	}

	private int[] parseAttacks(String attacks) {
		int[] r = new int[] { 0, 0 };
		if (attacks == null)
			return r;
		String[] parts = attacks.trim().split("[/／]");
		if (parts.length == 2) {
			r[0] = toInt(parts[0].trim(), 0);
			r[1] = toInt(parts[1].trim(), 0);
		}
		else if (parts.length == 1) {
			r[0] = toInt(parts[0].trim(), 0);
		}
		return r;
	}

	private static double toDouble(Object o) {
		if (o == null)
			return 0;
		try {
			String s = String.valueOf(o).trim().replace("%", "").replace("％", "");
			return Double.parseDouble(s);
		}
		catch (Exception e) {
			return 0;
		}
	}

	private static Integer parseIntOrNull(String s) {
		if (s == null || s.isEmpty())
			return null;
		try {
			return Integer.parseInt(s.trim());
		}
		catch (Exception e) {
			return null;
		}
	}

	private static String asString(Object o) {
		return o == null ? null : String.valueOf(o).trim();
	}

	private static String orDefault(String v, String def) {
		return (v == null || v.isEmpty()) ? def : v;
	}

	private static int toInt(Object o, int def) {
		if (o == null)
			return def;
		try {
			if (o instanceof Number)
				return ((Number) o).intValue();
			String s = String.valueOf(o).trim();
			// 提取首个连续数字（处理 OCR 输出 "21★"、"20 %"、"700.0" 等带符号的数字）
			Matcher m = Pattern.compile("^(-?\\d+)").matcher(s);
			if (m.find())
				return Integer.parseInt(m.group(1));
			return Integer.parseInt(s);
		}
		catch (Exception e) {
			return def;
		}
	}

	private static final String EXAMPLE_JSON = "{\n"
			+ "  \"metadata\": { \"source_folder\": \"示例\", \"mode\": \"json\" },\n" + "  \"data\": [\n"
			+ "    {\"rank\":1,\"name\":\"示例成员\",\"stars\":21,\"destruction\":700.0,\"attacks\":\"7/7\"},\n"
			+ "    {\"rank\":2,\"name\":\"另一位成员\",\"stars\":18,\"destruction\":600.0,\"attacks\":\"6/7\"}\n" + "  ],\n"
			+ "  \"records\": [[1,\"示例成员\",21,700.0,\"7/7\"],[2,\"另一位成员\",18,600.0,\"6/7\"]]\n" + "}";

}
