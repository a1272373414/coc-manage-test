package com.tencent.wxcloudrun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * 联赛战绩图片导入的 OCR 识别服务。
 *
 * 调用腾讯云 OCR 表格识别V3（RecognizeTableAccurateOCR），API 直接返回 Base64 编码的
 * Excel 文件，用 POI 读取为行数据后交给控制器做字段解析。
 *
 * 签名算法：TC3-HMAC-SHA256（与 Python 版 image_to_excel_oneclick.py 的 tencent 模式一致）。
 * 密钥通过 application.yml 的 tencent.ocr.* 配置。
 */
@Service
public class LeagueImageOcrService {

  @Value("${tencent.ocr.secret-id:}")
  private String secretId;

  @Value("${tencent.ocr.secret-key:}")
  private String secretKey;

  @Value("${tencent.ocr.region:ap-guangzhou}")
  private String region;

  @Value("${tencent.ocr.endpoint:ocr.tencentcloudapi.com}")
  private String endpoint;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * 对单张图片执行 OCR 表格识别，返回识别出的表格行（每行为单元格文本数组）。
   * 识别失败抛异常，由调用方决定降级处理。
   */
  public List<String[]> ocrToRows(MultipartFile file) throws Exception {
    if (secretId == null || secretId.isEmpty() || secretKey == null || secretKey.isEmpty()) {
      throw new IllegalStateException("未配置腾讯云OCR密钥(tencent.ocr.secret-id/secret-key)");
    }
    String imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());
    Map<String, Object> bodyObj = new LinkedHashMap<>();
    bodyObj.put("ImageBase64", imageBase64);
    bodyObj.put("UseNewModel", true);
    String body = MAPPER.writeValueAsString(bodyObj);

    String resp = postTencentOcr(body);
    JsonNode root = MAPPER.readTree(resp);
    JsonNode respNode = root.path("Response");
    JsonNode err = respNode.path("Error");
    if (!err.isMissingNode() && !err.isEmpty()) {
      throw new RuntimeException("腾讯云OCR错误: " + err.path("Code").asText() + " " + err.path("Message").asText());
    }
    String dataB64 = respNode.path("Data").asText("");
    if (dataB64.isEmpty()) return Collections.emptyList();
    byte[] excelBytes = Base64.getDecoder().decode(dataB64);
    return readExcelRows(excelBytes);
  }

  /** 读取 Excel 字节流第一个 sheet 的所有非空行，每行转为字符串数组 */
  private List<String[]> readExcelRows(byte[] excelBytes) throws Exception {
    List<String[]> rows = new ArrayList<>();
    Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(excelBytes));
    try {
      Sheet sheet = wb.getSheetAt(0);
      for (int r = 0; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) continue;
        int last = row.getLastCellNum();
        List<String> cells = new ArrayList<>();
        boolean any = false;
        for (int c = 0; c < last; c++) {
          String v = cellToStr(row.getCell(c));
          cells.add(v);
          if (v != null && !v.isEmpty()) any = true;
        }
        if (any) rows.add(cells.toArray(new String[0]));
      }
    } finally {
      wb.close();
    }
    return rows;
  }

  private String cellToStr(Cell cell) {
    if (cell == null) return "";
    try {
      switch (cell.getCellType()) {
        case STRING:
          return cell.getStringCellValue().trim();
        case NUMERIC:
          double d = cell.getNumericCellValue();
          if (d == Math.floor(d)) return String.valueOf((long) d);
          return String.valueOf(d);
        case BOOLEAN:
          return String.valueOf(cell.getBooleanCellValue());
        case FORMULA:
          return cell.getStringCellValue();
        default:
          return "";
      }
    } catch (Exception e) {
      return "";
    }
  }

  // ==================== 腾讯云 TC3-HMAC-SHA256 签名 + POST ====================

  private String postTencentOcr(String body) throws Exception {
    String service = "ocr";
    String action = "RecognizeTableAccurateOCR";
    String version = "2018-11-19";
    long ts = System.currentTimeMillis() / 1000L;
    String timestamp = String.valueOf(ts);

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    String date = sdf.format(new Date(ts * 1000L));

    String payloadHash = sha256Hex(body);
    // 规范头：小写键名 + trim 值，X-TC-Action 的值需转小写
    String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
        + "host:" + endpoint + "\n"
        + "x-tc-action:" + action.toLowerCase() + "\n";
    String signedHeaders = "content-type;host;x-tc-action";
    String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
    String credentialScope = date + "/" + service + "/tc3_request";
    String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);

    byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
    byte[] secretService = hmacSha256(secretDate, service);
    byte[] secretSigning = hmacSha256(secretService, "tc3_request");
    String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));

    String authorization = "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
        + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

    URL url = new URL("https://" + endpoint + "/");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setConnectTimeout(60000);
    conn.setReadTimeout(180000);
    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    conn.setRequestProperty("Host", endpoint);
    conn.setRequestProperty("X-TC-Action", action);
    conn.setRequestProperty("X-TC-Timestamp", timestamp);
    conn.setRequestProperty("X-TC-Version", version);
    conn.setRequestProperty("X-TC-Region", region);
    conn.setRequestProperty("Authorization", authorization);

    byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
    try (OutputStream os = conn.getOutputStream()) {
      os.write(bodyBytes);
    }
    int code = conn.getResponseCode();
    InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int n;
    while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
    String resp = new String(bos.toByteArray(), StandardCharsets.UTF_8);
    if (code >= 400) throw new RuntimeException("腾讯云OCR HTTP " + code + ": " + resp);
    return resp;
  }

  private static byte[] hmacSha256(byte[] key, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256Hex(String s) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    return bytesToHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
  }

  private static String bytesToHex(byte[] b) {
    StringBuilder sb = new StringBuilder();
    for (byte x : b) sb.append(String.format("%02x", x & 0xff));
    return sb.toString();
  }
}
