package com.tencent.wxcloudrun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * 腾讯云 OCR 通用文字识别高精度版（GeneralAccurateOCR）实现。
 *
 * API 返回 TextDetections[]，每项含 DetectedText 和 ItemPolygon{X,Y,Width,Height}，
 * 文字按从上到下、从左到右排序。
 *
 * 本实现利用位置信息（X/Y）将识别文字分配到 5 个固定列（排名、名称、星星、摧毁率、进攻次数），
 * 支持同列多词合并，并能检测漏识别的字段。
 *
 * 签名算法：TC3-HMAC-SHA256。密钥通过 application.yml 的 tencent.ocr.* 配置。
 */
@Service
@ConditionalOnProperty(name = "tencent.ocr.secret-id")
public class TencentImageOcrService implements LeagueImageOcrService {

	private static final Logger log = LoggerFactory.getLogger(TencentImageOcrService.class);

	@Value("${tencent.ocr.secret-id:}")
	private String secretId;

	@Value("${tencent.ocr.secret-key:}")
	private String secretKey;

	@Value("${tencent.ocr.region:ap-guangzhou}")
	private String region;

	@Value("${tencent.ocr.endpoint:ocr.tencentcloudapi.com}")
	private String endpoint;

	/** 联赛战绩图片每行的固定列数（排名、名称、星星数、摧毁数、进攻数） */
	private static final int COLUMNS_PER_ROW = 5;

	/** 列分界的最小间距阈值（同列内 X 差值 < 阈值，列间差值 > 阈值） */
	private static final int COL_GAP_THRESHOLD = 60;

	/** 同一行内 Y 坐标最大允许差值（约半行高，行间距~80px） */
	private static final int ROW_Y_TOLERANCE = 35;

	/** 当动态边界检测不足时使用的默认边界（来自样本图片的统计） */
	private static final int[] FALLBACK_COL_BOUNDARIES = {110, 550, 700, 880};

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public List<String[]> ocrToRows(MultipartFile file) throws Exception {
		String fn = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		if (secretId == null || secretId.isEmpty() || secretKey == null || secretKey.isEmpty()) {
			throw new IllegalStateException("未配置腾讯云OCR密钥(tencent.ocr.secret-id/secret-key)");
		}
		long size = file.getSize();
		log.info("[腾讯OCR] 调用开始: file={}, size={} bytes", fn, size);
		String imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());
		Map<String, Object> bodyObj = new LinkedHashMap<>();
		bodyObj.put("ImageBase64", imageBase64);
		String body = MAPPER.writeValueAsString(bodyObj);
		log.info("[腾讯OCR] 请求 body 大小: {} bytes (base64 长度 {})", body.length(), imageBase64.length());

		String resp = postTencentOcr(body);
		JsonNode root = MAPPER.readTree(resp);
		JsonNode respNode = root.path("Response");
		JsonNode err = respNode.path("Error");
		if (!err.isMissingNode() && !err.isEmpty()) {
			String code = err.path("Code").asText();
			String msg = err.path("Message").asText();
			log.error("[腾讯OCR] 调用失败: file={}, code={}, msg={}", fn, code, msg);
			throw new RuntimeException("腾讯云OCR错误: " + code + " " + msg);
		}

		JsonNode textDetections = respNode.path("TextDetections");
		int detectionsCnt = textDetections.isArray() ? textDetections.size() : 0;
		log.info("[腾讯OCR] 响应: file={}, TextDetections数量={}", fn, detectionsCnt);

		List<String[]> rows = parseTextDetections(textDetections, fn);
		log.info("[腾讯OCR] 解析完成: file={}, 共 {} 行", fn, rows.size());
		return rows;
	}

	/**
	 * 从 GeneralAccurateOCR 响应的 TextDetections 解析为二维表格行。
	 * 利用位置信息（Y/X）将文字分配到 5 列，支持同列多词合并，并能检测漏识别。
	 *
	 * 算法：
	 * 1. 提取所有 (DetectedText, Y, X) 并按 (Y, X) 排序
	 * 2. 从所有识别项中动态计算列边界（按 X 聚类，相邻间距 > COL_GAP_THRESHOLD 视为列间分界）
	 * 3. 按 Y 坐标聚类分行（Y 差值 &lt; ROW_Y_TOLERANCE 视为同行）
	 * 4. 每行内按 X 坐标分配到 5 列
	 * 5. 同一列内多个词合并（按 X 升序拼接）
	 * 6. 空列保留为 ""（漏识别检测）
	 */
	private List<String[]> parseTextDetections(JsonNode textDetections, String fn) {
		List<String[]> rows = new ArrayList<>();
		if (textDetections == null || !textDetections.isArray() || textDetections.size() == 0) {
			log.warn("[腾讯OCR] TextDetections 为空: file={}", fn);
			return rows;
		}

		// 1. 提取 (text, Y, X) 三元组（腾讯云使用 X/Y 坐标，对应 Baidu 的 left/top）
		List<WordItem> items = new ArrayList<>();
		for (JsonNode node : textDetections) {
			String text = node.path("DetectedText").asText("").trim();
			if (text.isEmpty()) continue;
			JsonNode polygon = node.path("ItemPolygon");
			int x = polygon.path("X").asInt(0);
			int y = polygon.path("Y").asInt(0);
			items.add(new WordItem(text, y, x));
		}

		if (items.isEmpty()) {
			log.warn("[腾讯OCR] TextDetections 中没有有效文字: file={}", fn);
			return rows;
		}

		// 按 Y -> X 排序（自然阅读顺序：从上到下，从左到右）
		items.sort(java.util.Comparator.comparingInt((WordItem w) -> w.y)
				.thenComparingInt(w -> w.x));

		log.info("[腾讯OCR] 共识别 {} 个文字项，开始按位置分行分列", items.size());

		// 2. 从所有项中动态计算列边界（自适应不同图片的起始坐标偏移）
		int[] colBoundaries = computeColumnBoundariesFromX(items);
		log.info("[腾讯OCR] 动态列边界: [{}, {}, {}, {}]", colBoundaries[0], colBoundaries[1], colBoundaries[2], colBoundaries[3]);

		// 3. 按 Y 聚类分行
		List<List<WordItem>> rowGroups = new ArrayList<>();
		List<WordItem> currentRow = new ArrayList<>();
		int rowYBaseline = items.get(0).y;

		for (WordItem item : items) {
			if (Math.abs(item.y - rowYBaseline) <= ROW_Y_TOLERANCE) {
				currentRow.add(item);
			} else {
				rowGroups.add(currentRow);
				currentRow = new ArrayList<>();
				currentRow.add(item);
				rowYBaseline = item.y;
			}
		}
		if (!currentRow.isEmpty()) {
			rowGroups.add(currentRow);
		}

		log.info("[腾讯OCR] 分行结果: {} 行", rowGroups.size());

		// 4. 每行内按 X 分配到 5 列，同列多词合并
		int rowIdx = 0;
		for (int ri = 0; ri < rowGroups.size(); ri++) {
			List<WordItem> group = rowGroups.get(ri);
			// 按 X 排序（同行内从左到右）
			group.sort(java.util.Comparator.comparingInt(w -> w.x));

			String[] row = new String[COLUMNS_PER_ROW];
			java.util.Arrays.fill(row, "");

			for (WordItem w : group) {
				int col = getColumnByX(w.x, colBoundaries);
				// 同列多词 → 合并
				if (row[col].isEmpty()) {
					row[col] = w.text;
				} else {
					row[col] = row[col] + w.text;
				}
			}

			// 前 5 行打印详细内容，便于诊断
			if (rowIdx < 5) {
				StringBuilder sb = new StringBuilder("[");
				for (String v : row)
					sb.append("\"").append(v).append("\",");
				if (sb.length() > 1)
					sb.setLength(sb.length() - 1);
				sb.append("]");
				log.info("[腾讯OCR] 行 {}: {}", ri, sb);
			}

			// 检测空列，记录警告
			for (int col = 0; col < COLUMNS_PER_ROW; col++) {
				if (row[col].isEmpty()) {
					log.warn("[腾讯OCR] 行 {} 列 {} (X区间{}) 可能漏识别: file={}",
							ri, col, getColumnRange(col, colBoundaries), fn);
				}
			}

			rows.add(row);
			rowIdx++;
		}

		return rows;
	}

	/**
	 * 从所有识别项的 X 坐标中动态计算列边界。
	 * 将 X 值从小到大排序，相邻值间距 > COL_GAP_THRESHOLD 视为列间分界，
	 * 取相邻值的中点作为列边界。检测不足 4 个边界时回退到默认值。
	 */
	private int[] computeColumnBoundariesFromX(List<WordItem> items) {
		List<Integer> xs = new ArrayList<>();
		for (WordItem w : items) {
			xs.add(w.x);
		}
		java.util.Collections.sort(xs);

		List<Integer> bounds = new ArrayList<>();
		for (int i = 1; i < xs.size(); i++) {
			int gap = xs.get(i) - xs.get(i - 1);
			if (gap > COL_GAP_THRESHOLD) {
				int boundary = xs.get(i - 1) + gap / 2;
				bounds.add(boundary);
			}
		}

		if (bounds.size() >= 4) {
			int[] result = new int[4];
			for (int i = 0; i < 4; i++) {
				result[i] = bounds.get(i);
			}
			return result;
		}

		log.warn("[腾讯OCR] 动态列边界只检测到 {} 个分隔点（期望4个），使用默认边界", bounds.size());
		return FALLBACK_COL_BOUNDARIES;
	}

	/**
	 * 根据 X 坐标和动态计算的列边界判断属于哪一列。
	 */
	private int getColumnByX(int x, int[] boundaries) {
		for (int i = 0; i < boundaries.length; i++) {
			if (x < boundaries[i]) return i;
		}
		return COLUMNS_PER_ROW - 1;
	}

	/** 获取列区间描述文本（用于日志告警） */
	private String getColumnRange(int col, int[] boundaries) {
		String[] names = {"排名", "名称", "星星", "摧毁", "进攻"};
		if (col < 0 || col >= COLUMNS_PER_ROW) return "未知";
		if (col == 0) return "0~" + boundaries[0] + " (" + names[0] + ")";
		if (col == COLUMNS_PER_ROW - 1) return "≥" + boundaries[boundaries.length - 1] + " (" + names[col] + ")";
		return boundaries[col - 1] + "~" + boundaries[col] + " (" + names[col] + ")";
	}

	/** 识别文字的内部结构体（X/Y 对应腾讯云的坐标体系） */
	private static class WordItem {
		final String text;
		final int y;
		final int x;

		WordItem(String text, int y, int x) {
			this.text = text;
			this.y = y;
			this.x = x;
		}
	}

	// ==================== 腾讯云 TC3-HMAC-SHA256 签名 + POST ====================

	private String postTencentOcr(String body) throws Exception {
		String service = "ocr";
		String action = "GeneralAccurateOCR";
		String version = "2018-11-19";
		long ts = System.currentTimeMillis() / 1000L;
		String timestamp = String.valueOf(ts);

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		String date = sdf.format(new Date(ts * 1000L));

		String payloadHash = sha256Hex(body);
		// 规范头：小写键名 + trim 值，X-TC-Action 的值需转小写
		String canonicalHeaders = "content-type:application/json; charset=utf-8\n" + "host:" + endpoint + "\n"
				+ "x-tc-action:" + action.toLowerCase() + "\n";
		String signedHeaders = "content-type;host;x-tc-action";
		String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
		String credentialScope = date + "/" + service + "/tc3_request";
		String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n"
				+ sha256Hex(canonicalRequest);

		byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
		byte[] secretService = hmacSha256(secretDate, service);
		byte[] secretSigning = hmacSha256(secretService, "tc3_request");
		String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));

		String authorization = "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope + ", SignedHeaders="
				+ signedHeaders + ", Signature=" + signature;

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
		while ((n = is.read(buf)) != -1)
			bos.write(buf, 0, n);
		String resp = new String(bos.toByteArray(), StandardCharsets.UTF_8);
		if (code >= 400)
			throw new RuntimeException("腾讯云OCR HTTP " + code + ": " + resp);
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
		for (byte x : b)
			sb.append(String.format("%02x", x & 0xff));
		return sb.toString();
	}

}
