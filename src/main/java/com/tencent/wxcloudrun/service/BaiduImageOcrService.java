package com.tencent.wxcloudrun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 百度智能云 OCR 网络图片文字识别（含位置信息版）实现
 * （接口：POST https://aip.baidubce.com/rest/2.0/ocr/v1/webimage_loc）。
 *
 * 接口说明： - 通过 access_token 鉴权（用 API Key + Secret Key 换取，有效期 30 天） -
 * 请求参数：image(Base64)/url、detect_direction、detect_language、paragraph、probability 等 -
 * 响应：words_result[]，每个元素含 words 和 location{top, left, width, height}，
 *       文字按从上到下、从左到右排序。
 *
 * 本实现利用位置信息（left/top）将识别文字分配到 5 个固定列（排名、名称、星星、摧毁率、进攻次数），
 * 支持同一列多词合并（如裂开的星星数字+符号），并能检测漏识别的字段。
 */
@Service
@ConditionalOnProperty(name = "baidu.ocr.api-key")
public class BaiduImageOcrService implements LeagueImageOcrService {

	private static final Logger log = LoggerFactory.getLogger(BaiduImageOcrService.class);

	@Value("${baidu.ocr.api-key:}")
	private String apiKey;

	@Value("${baidu.ocr.secret-key:}")
	private String secretKey;

	@Value("${baidu.ocr.endpoint:https://aip.baidubce.com}")
	private String endpoint;

	@Value("${baidu.ocr.webimage-loc-path:/rest/2.0/ocr/v1/webimage_loc}")
	private String webImageLocPath;

	/** 联赛战绩图片每行的固定列数（排名、名称、星星数、摧毁数、进攻数） */
	private static final int COLUMNS_PER_ROW = 5;

	/** 列分界的最小间距阈值（同列内 X 差值 < 阈值，列间差值 > 阈值） */
	private static final int COL_GAP_THRESHOLD = 60;

	/** 同一行内 top 坐标最大允许差值（约半行高，行间距~80px） */
	private static final int ROW_TOP_TOLERANCE = 35;

	/** 当动态边界检测不足时使用的默认边界（来自 7 张样本图片的统计） */
	private static final int[] FALLBACK_COL_BOUNDARIES = {120, 550, 720, 880};

	@Value("${baidu.ocr.token-path:/oauth/2.0/token}")
	private String tokenPath;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** access_token 缓存 */
	private volatile String cachedToken;

	/** token 过期时间（毫秒时间戳），提前 5 分钟刷新 */
	private volatile long tokenExpireAt;

	@Override
	public List<String[]> ocrToRows(MultipartFile file) throws Exception {
		String fn = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		if (apiKey == null || apiKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
			throw new IllegalStateException("未配置百度OCR密钥(baidu.ocr.api-key/secret-key)");
		}
		long size = file.getSize();
		log.info("[百度OCR] 调用开始: file={}, size={} bytes", fn, size);

		String token = getAccessToken();
		String imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());

		// 请求参数：webimage_loc 接口参数
		Map<String, String> params = new LinkedHashMap<>();
		params.put("image", imageBase64);
		params.put("detect_direction", "false");
		params.put("detect_language", "false");
		params.put("paragraph", "false");
		params.put("probability", "false");
		String formBody = formEncode(params);
		log.info("[百度OCR] 请求 body 大小: {} bytes (base64 长度 {})", formBody.length(), imageBase64.length());

		String url = endpoint + webImageLocPath + "?access_token=" + URLEncoder.encode(token, "UTF-8");
		String resp = postForm(url, formBody);
		JsonNode root = MAPPER.readTree(resp);

		// 错误检查
		JsonNode errCode = root.path("error_code");
		if (!errCode.isMissingNode() && errCode.asLong() != 0) {
			String errMsg = root.path("error_msg").asText("未知错误");
			log.error("[百度OCR] 调用失败: file={}, code={}, msg={}", fn, errCode.asLong(), errMsg);
			throw new RuntimeException("百度OCR错误: " + errCode.asLong() + " " + errMsg);
		}

		long logId = root.path("log_id").asLong(0L);
		JsonNode wordsResult = root.path("words_result");
		int wordsCnt = wordsResult.isArray() ? wordsResult.size() : 0;
		log.info("[百度OCR] 响应: file={}, log_id={}, words_result数量={}", fn, logId, wordsCnt);

		List<String[]> rows = parseWordsResult(wordsResult, fn);
		log.info("[百度OCR] 解析完成: file={}, 共 {} 行", fn, rows.size());
		return rows;
	}

	/**
	 * 从 webimage_loc OCR 响应中解析 words_result 为二维表格行。
	 * 利用位置信息（top/left）将文字分配到 5 列，支持同列多词合并（如裂开的"18"+"★"）。
	 *
	 * 算法：
	 * 1. 提取所有 (words, top, left) 并按 (top, left) 排序
	 * 2. 从所有识别项中动态计算列边界（按 left 聚类，相邻间距 > COL_GAP_THRESHOLD 视为列间分界）
	 * 3. 按 top 坐标聚类分行（top 差值 &lt; ROW_TOP_TOLERANCE 视为同行）
	 * 4. 每行内按 left 坐标分配到 5 列
	 * 5. 同一列内多个词合并（按 left 升序拼接）
	 * 6. 空列保留为 ""（漏识别检测）
	 */
	private List<String[]> parseWordsResult(JsonNode wordsResult, String fn) {
		List<String[]> rows = new ArrayList<>();
		if (wordsResult == null || !wordsResult.isArray() || wordsResult.size() == 0) {
			log.warn("[百度OCR] words_result 为空: file={}", fn);
			return rows;
		}

		// 1. 提取 (words, top, left) 三元组
		List<WordItem> items = new ArrayList<>();
		for (JsonNode node : wordsResult) {
			String words = node.path("words").asText("").trim();
			if (words.isEmpty()) continue;
			JsonNode loc = node.path("location");
			int top = loc.path("top").asInt(0);
			int left = loc.path("left").asInt(0);
			items.add(new WordItem(words, top, left));
		}

		if (items.isEmpty()) {
			log.warn("[百度OCR] words_result 中没有有效文字: file={}", fn);
			return rows;
		}

		// 按 top -> left 排序（自然阅读顺序）
		items.sort(java.util.Comparator.comparingInt((WordItem w) -> w.top)
				.thenComparingInt(w -> w.left));

		log.info("[百度OCR] 共识别 {} 个文字项，开始按位置分行分列", items.size());

		// 2. 从所有项中动态计算列边界（自适应不同图片的起始坐标偏移）
		int[] colBoundaries = computeColumnBoundariesFromLeft(items);
		log.info("[百度OCR] 动态列边界: [{}, {}, {}, {}]", colBoundaries[0], colBoundaries[1], colBoundaries[2], colBoundaries[3]);

		// 3. 按 top 聚类分行
		List<List<WordItem>> rowGroups = new ArrayList<>();
		List<WordItem> currentRow = new ArrayList<>();
		int rowTopBaseline = items.get(0).top;

		for (WordItem item : items) {
			if (Math.abs(item.top - rowTopBaseline) <= ROW_TOP_TOLERANCE) {
				currentRow.add(item);
			} else {
				rowGroups.add(currentRow);
				currentRow = new ArrayList<>();
				currentRow.add(item);
				rowTopBaseline = item.top;
			}
		}
		if (!currentRow.isEmpty()) {
			rowGroups.add(currentRow);
		}

		log.info("[百度OCR] 分行结果: {} 行", rowGroups.size());

		// 4. 每行内按 left 分配到 5 列，同列多词合并
		int rowIdx = 0;
		for (int ri = 0; ri < rowGroups.size(); ri++) {
			List<WordItem> group = rowGroups.get(ri);
			// 按 left 排序（同行内从左到右）
			group.sort(java.util.Comparator.comparingInt(w -> w.left));

			String[] row = new String[COLUMNS_PER_ROW];
			java.util.Arrays.fill(row, "");

			for (WordItem w : group) {
				int col = getColumnByLeft(w.left, colBoundaries);
				// 同列多词 → 合并
				if (row[col].isEmpty()) {
					row[col] = w.words;
				} else {
					row[col] = row[col] + w.words;
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
				log.info("[百度OCR] 行 {}: {}", ri, sb);
			}

			// 检测空列，记录警告
			for (int col = 0; col < COLUMNS_PER_ROW; col++) {
				if (row[col].isEmpty()) {
					log.warn("[百度OCR] 行 {} 列 {} (left区间{}) 可能漏识别: file={}",
							ri, col, getColumnRange(col, colBoundaries), fn);
				}
			}

			rows.add(row);
			rowIdx++;
		}

		return rows;
	}

	/**
	 * 从所有识别项的 left 坐标中动态计算列边界。
	 * 将 left 值从小到大排序，相邻值间距 > COL_GAP_THRESHOLD 视为列间分界，
	 * 取相邻值的中点作为列边界。检测不足 4 个边界时回退到默认值。
	 */
	private int[] computeColumnBoundariesFromLeft(List<WordItem> items) {
		List<Integer> lefts = new ArrayList<>();
		for (WordItem w : items) {
			lefts.add(w.left);
		}
		java.util.Collections.sort(lefts);

		List<Integer> bounds = new ArrayList<>();
		for (int i = 1; i < lefts.size(); i++) {
			int gap = lefts.get(i) - lefts.get(i - 1);
			if (gap > COL_GAP_THRESHOLD) {
				int boundary = lefts.get(i - 1) + gap / 2;
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

		log.warn("[百度OCR] 动态列边界只检测到 {} 个分隔点（期望4个），使用默认边界", bounds.size());
		return FALLBACK_COL_BOUNDARIES;
	}

	/**
	 * 根据 left 坐标和动态计算的列边界判断属于哪一列。
	 */
	private int getColumnByLeft(int left, int[] boundaries) {
		for (int i = 0; i < boundaries.length; i++) {
			if (left < boundaries[i]) return i;
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

	/** 识别文字的内部结构体 */
	private static class WordItem {
		final String words;
		final int top;
		final int left;

		WordItem(String words, int top, int left) {
			this.words = words;
			this.top = top;
			this.left = left;
		}
	}

	// ==================== 百度 access_token 获取 ====================

	/**
	 * 获取百度 access_token。 缓存 token，提前 5 分钟刷新，避免每次调用都换取。
	 */
	private synchronized String getAccessToken() throws Exception {
		long now = System.currentTimeMillis();
		if (cachedToken != null && now < tokenExpireAt) {
			return cachedToken;
		}
		String url = endpoint + tokenPath + "?grant_type=client_credentials" + "&client_id="
				+ URLEncoder.encode(apiKey, "UTF-8") + "&client_secret=" + URLEncoder.encode(secretKey, "UTF-8");
		String resp = postForm(url, "");
		JsonNode root = MAPPER.readTree(resp);
		JsonNode errCode = root.path("error");
		if (!errCode.isMissingNode() && !errCode.asText("").isEmpty()) {
			String errDesc = root.path("error_description").asText("");
			throw new RuntimeException("百度OCR获取token失败: " + errCode.asText() + " " + errDesc);
		}
		String token = root.path("access_token").asText("");
		long expiresIn = root.path("expires_in").asLong(2592000L);
		if (token.isEmpty())
			throw new RuntimeException("百度OCR获取token失败: 响应无 access_token");
		cachedToken = token;
		tokenExpireAt = now + (expiresIn - 300) * 1000L;
		log.info("[百度OCR] 获取 access_token 成功，有效期 {} 秒，下次刷新时间 {}", expiresIn,
				new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(tokenExpireAt)));
		return token;
	}

	// ==================== HTTP 工具 ====================

	private String postForm(String urlStr, String formBody) throws Exception {
		URL url = new URL(urlStr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setConnectTimeout(60000);
		conn.setReadTimeout(180000);
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		byte[] bodyBytes = formBody.getBytes(StandardCharsets.UTF_8);
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
			throw new RuntimeException("百度OCR HTTP " + code + ": " + resp);
		return resp;
	}

	private static String formEncode(Map<String, String> params) throws Exception {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : params.entrySet()) {
			if (sb.length() > 0)
				sb.append("&");
			sb.append(URLEncoder.encode(e.getKey(), "UTF-8"));
			sb.append("=");
			sb.append(URLEncoder.encode(e.getValue(), "UTF-8"));
		}
		return sb.toString();
	}

}
