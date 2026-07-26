package com.tencent.wxcloudrun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.config.UserContext;
import com.tencent.wxcloudrun.entity.biz.ClanMember;
import com.tencent.wxcloudrun.mapper.ClanMemberMapper;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部落成员 Excel 批量导入。
 * 导入时按成员名称（member_name）在「同一群组(group_no) + 部落(clan_no)」范围内查重，已存在则忽略（跳过）。
 */
@RestController
@RequestMapping("/api/clan/member/import")
public class ClanMemberImportController {

  @Autowired
  private ClanMemberMapper clanMemberMapper;

  /** 预览：解析 Excel 并标注每条记录是否已存在（按成员名称查重）。 */
  @PostMapping("/preview")
  public ApiResponse preview(
      @RequestParam("type") String type,
      @RequestParam(value = "clanNo", required = false) String clanNo,
      @RequestParam(value = "groupNo", required = false) String groupNo,
      @RequestParam("files") MultipartFile file) {
    if (!"excel".equalsIgnoreCase(type)) {
      return ApiResponse.error("仅支持 Excel 导入");
    }
    if (clanNo == null || clanNo.trim().isEmpty()) {
      return ApiResponse.error("请选择部落");
    }
    String g = resolveGroupNo(groupNo);
    if (g == null || g.trim().isEmpty()) {
      return ApiResponse.error("无法获取群组信息，请确认登录状态");
    }

    List<ClanMemberRow> rows;
    try {
      rows = parseExcel(file.getInputStream());
    } catch (Exception e) {
      return ApiResponse.error("Excel 解析失败：" + e.getMessage());
    }
    if (rows.isEmpty()) {
      return ApiResponse.error("未从 Excel 中解析到成员数据");
    }

    // 批量拉取该部落已存在的成员名称/编号，本地按“编号/名称”条件查重
    ExistSets exist = loadExist(g, clanNo);

    for (ClanMemberRow row : rows) {
      boolean hasNo = row.memberNo != null && !row.memberNo.trim().isEmpty();
      row.exists = hasNo
          ? exist.nos.contains(row.memberNo.trim())
          : exist.names.contains(row.memberName.trim());
    }

    Map<String, Object> data = new HashMap<>(4);
    data.put("records", rows);
    data.put("clanNo", clanNo);
    data.put("groupNo", g);
    return ApiResponse.ok(data);
  }

  /** 确认导入：跳过已存在的成员（按成员名称查重），其余插入。 */
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

    ExistSets exist = loadExist(g, clanNo);
    int inserted = 0;
    int skipped = 0;

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
      boolean hasNo = no != null;
      // 条件唯一：填了编号 → 校验编号唯一；没填编号 → 校验名称唯一
      boolean dup = hasNo ? exist.nos.contains(no) : exist.names.contains(name);
      if (dup) {
        skipped++;
        continue;
      }
      ClanMember member = new ClanMember();
      member.setGroupNo(g);
      member.setClanNo(clanNo);
      member.setMemberName(name);
      member.setMemberNo(no);
      member.setWarStatus(0);
      clanMemberMapper.insert(member);
      // 把新插入的名称/编号加入已存在集合，避免同一批次内重复插入
      if (hasNo) {
        exist.nos.add(no);
      } else {
        exist.names.add(name);
      }
      inserted++;
    }

    Map<String, Object> data = new HashMap<>(4);
    data.put("inserted", inserted);
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
      // 示例行（编号可选）
      Row sample = sheet.createRow(1);
      sample.createCell(0).setCellValue("张三");
      sample.createCell(1).setCellValue("");
      sheet.autoSizeColumn(0);
      sheet.autoSizeColumn(1);
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
        r.exists = false;
        rows.add(r);
      }
    }
    return rows;
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

  private String resolveGroupNo(String groupNo) {
    String g = (groupNo == null || groupNo.trim().isEmpty()) ? UserContext.getGroupNo() : groupNo;
    if (g == null || g.trim().isEmpty()) {
      return null;
    }
    return g.trim();
  }

  private ExistSets loadExist(String groupNo, String clanNo) {
    QueryWrapper<ClanMember> qw = new QueryWrapper<>();
    qw.eq("group_no", groupNo);
    if (clanNo != null && !clanNo.trim().isEmpty()) {
      qw.eq("clan_no", clanNo);
    }
    qw.select("member_name", "member_no");
    List<ClanMember> list = clanMemberMapper.selectList(qw);
    ExistSets s = new ExistSets();
    if (list != null) {
      for (ClanMember m : list) {
        if (m.getMemberName() != null) {
          s.names.add(m.getMemberName().trim());
        }
        if (m.getMemberNo() != null && !m.getMemberNo().trim().isEmpty()) {
          s.nos.add(m.getMemberNo().trim());
        }
      }
    }
    return s;
  }

  /** 已存在成员的名称/编号集合（按 group_no + clan_no 范围）。 */
  private static class ExistSets {
    Set<String> names = new LinkedHashSet<>();
    Set<String> nos = new LinkedHashSet<>();
  }

  /** 解析后的成员行（excel 预览用）。 */
  public static class ClanMemberRow {
    public String memberName;
    public String memberNo;
    public boolean exists;
  }
}
