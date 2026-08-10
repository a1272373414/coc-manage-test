package com.tencent.wxcloudrun.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 联赛战绩图片 OCR 综合管理服务。
 *
 * 并行调用所有已注册的 OCR 实现（百度 + 腾讯），择优合并返回结果。 当所有 OCR 都失败/为空时返回空列表，由调用方降级处理。
 *
 * 当前注册的实现： - {@link BaiduImageOcrService}（优先级 1，需配置 baidu.ocr.secret-id） -
 * {@link TencentImageOcrService}（优先级 2，需配置 tencent.ocr.secret-id）
 *
 * 综合策略： 1. 百度 + 腾讯均成功 → 以百度结果为基准，百度漏识别或空值的单元格用腾讯数据填充 2. 仅百度成功 → 返回百度结果 3.
 * 仅腾讯成功 → 返回腾讯结果 4. 二者均失败 → 返回空列表
 */
@Service
public class LeagueImageOcrManager {

	private static final Logger log = LoggerFactory.getLogger(LeagueImageOcrManager.class);

	private final List<LeagueImageOcrService> services;

	private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "ocr-worker");
		t.setDaemon(true);
		return t;
	});

	@Autowired
	public LeagueImageOcrManager(List<LeagueImageOcrService> services) {
		// 排序：BaiduImageOcrService 优先，其它其次
		List<LeagueImageOcrService> sorted = new ArrayList<>(services);
		sorted.sort((a, b) -> {
			int pa = priorityOf(a);
			int pb = priorityOf(b);
			return Integer.compare(pa, pb);
		});
		this.services = sorted;
		log.info("LeagueImageOcrManager 初始化完成，注册 OCR 服务: {}", classNames(this.services));
	}

	private int priorityOf(LeagueImageOcrService s) {
		// Baidu 优先级 1（最高），其它优先级 2
		if (s instanceof BaiduImageOcrService)
			return 1;
		return 2;
	}

	private List<String> classNames(List<LeagueImageOcrService> list) {
		List<String> names = new ArrayList<>();
		for (LeagueImageOcrService s : list)
			names.add(s.getClass().getSimpleName());
		return names;
	}

	/**
	 * 综合调用 OCR 服务识别图片，返回首个非空结果。 实现细节：并行调用所有服务，按优先级返回首个非空结果。 所有服务都失败/为空时返回空列表。
	 */
	public List<String[]> ocrToRows(MultipartFile file) {
		String fn = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		if (services.isEmpty()) {
			log.warn("无可用 OCR 服务，请检查 application-local.yml 配置（baidu.ocr.* / tencent.ocr.*）");
			return Collections.emptyList();
		}

		// 并行调用所有服务
		List<Future<OcrResult>> futures = new ArrayList<>();
		for (final LeagueImageOcrService svc : services) {
			futures.add(executor.submit(new Callable<OcrResult>() {
				@Override
				public OcrResult call() {
					OcrResult r = new OcrResult();
					r.serviceName = svc.getClass().getSimpleName();
					long start = System.currentTimeMillis();
					try {
						r.rows = svc.ocrToRows(file);
						r.success = r.rows != null && !r.rows.isEmpty();
						r.rowCount = r.rows == null ? 0 : r.rows.size();
					}
					catch (Exception e) {
						r.success = false;
						r.error = e.getMessage();
						log.warn("[{}] OCR 调用异常: file={}, msg={}", r.serviceName, fn, e.getMessage());
					}
					r.elapsedMs = System.currentTimeMillis() - start;
					return r;
				}
			}));
		}

		// 等待所有结果（最多 120 秒）
		List<OcrResult> results = new ArrayList<>();
		for (Future<OcrResult> f : futures) {
			try {
				results.add(f.get(120, TimeUnit.SECONDS));
			}
			catch (Exception e) {
				log.error("等待 OCR 结果超时或异常: file={}", fn, e);
			}
		}

		// 提取百度与腾讯结果
		OcrResult baidu = findResult(results, "BaiduImageOcrService");
		OcrResult tencent = findResult(results, "TencentImageOcrService");

		// 2.1 双方都成功 → 以百度为基准，空值用腾讯填充
		if (baidu != null && baidu.success && tencent != null && tencent.success) {
			List<String[]> merged = mergeResults(baidu.rows, tencent.rows, fn);
			log.info("OCR 综合结果: file={}, 选用 百度+腾讯合并 数据，{} 行", fn, merged.size());
			printSummary(fn, results);
			return merged;
		}

		// 2.2 仅百度成功
		if (baidu != null && baidu.success) {
			log.info("OCR 综合结果: file={}, 选用 BaiduImageOcrService 数据，{} 行，耗时 {} ms",
					fn, baidu.rowCount, baidu.elapsedMs);
			printSummary(fn, results);
			return baidu.rows;
		}

		// 2.3 仅腾讯成功
		if (tencent != null && tencent.success) {
			log.info("OCR 综合结果: file={}, 选用 TencentImageOcrService 数据，{} 行，耗时 {} ms",
					fn, tencent.rowCount, tencent.elapsedMs);
			printSummary(fn, results);
			return tencent.rows;
		}

		// 2.4 按注册顺序回退到其他服务
		for (LeagueImageOcrService svc : services) {
			String svcName = svc.getClass().getSimpleName();
			for (OcrResult r : results) {
				if (svcName.equals(r.serviceName) && r.success) {
					log.info("OCR 综合结果: file={}, 选用 {} 数据，{} 行，耗时 {} ms",
							fn, svcName, r.rowCount, r.elapsedMs);
					printSummary(fn, results);
					return r.rows;
				}
			}
		}

		// 全部失败
		log.warn("OCR 综合结果: file={}, 所有服务均无可用数据", fn);
		printSummary(fn, results);
		return Collections.emptyList();
	}

	/**
	 * 在结果列表中查找指定服务名的结果。
	 */
	private OcrResult findResult(List<OcrResult> results, String serviceName) {
		for (OcrResult r : results) {
			if (serviceName.equals(r.serviceName)) {
				return r;
			}
		}
		return null;
	}

	/**
	 * 合并百度与腾讯的识别结果：以百度行为基准，百度行中空值的单元格用腾讯对应位置的单元格值填充。
	 * 若腾讯识别出更多行，超出部分追加到末尾。
	 */
	private List<String[]> mergeResults(List<String[]> baiduRows, List<String[]> tencentRows, String fileName) {
		int baiduCnt = baiduRows.size();
		int tencentCnt = tencentRows.size();
		int fillCount = 0;
		int tencentExtraRows = 0;

		int minRows = Math.min(baiduCnt, tencentCnt);
		for (int ri = 0; ri < minRows; ri++) {
			String[] baiduRow = baiduRows.get(ri);
			String[] tencentRow = tencentRows.get(ri);
			int minCols = Math.min(baiduRow.length, tencentRow.length);
			for (int ci = 0; ci < minCols; ci++) {
				if (isEmpty(baiduRow[ci]) && !isEmpty(tencentRow[ci])) {
					String filledValue = tencentRow[ci];
					baiduRow[ci] = filledValue;
					fillCount++;
					log.debug("OCR 合并填充: file={}, row={}, col={}, 腾讯值={}", fileName, ri, ci, filledValue);
				}
			}
		}

		// 腾讯识别出更多行时，追加到末尾
		if (tencentCnt > baiduCnt) {
			for (int ri = baiduCnt; ri < tencentCnt; ri++) {
				baiduRows.add(tencentRows.get(ri));
				tencentExtraRows++;
			}
		}

		log.info("OCR 合并: file={}, 百度{}行, 腾讯{}行, 填充{}个空值, 追加{}行",
				fileName, baiduCnt, tencentCnt, fillCount, tencentExtraRows);
		return baiduRows;
	}

	/**
	 * 判断字符串是否为空（null、trim 后为空、或为常见的占位符/空标记）。
	 */
	private boolean isEmpty(String s) {
		if (s == null) {
			return true;
		}
		String trimmed = s.trim();
		if (trimmed.isEmpty()) {
			return true;
		}
		// 表格占位符、空标记等也视为空
		switch (trimmed) {
			case "-":
			case "--":
			case "—":
			case "——":
			case "/":
			case "N/A":
			case "n/a":
			case "NA":
			case "无":
			case "无数据":
			case "null":
				return true;
			default:
				return false;
		}
	}

	private void printSummary(String fn, List<OcrResult> results) {
		for (OcrResult r : results) {
			log.info("  - {} : success={}, rows={}, elapsed={} ms, error={}",
					r.serviceName, r.success, r.rowCount, r.elapsedMs, r.error);
		}
	}

	private static class OcrResult {
		String serviceName;

		boolean success;

		List<String[]> rows;

		int rowCount;

		long elapsedMs;

		String error;
	}

}
