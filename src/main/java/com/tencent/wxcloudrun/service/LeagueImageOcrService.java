package com.tencent.wxcloudrun.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 联赛战绩图片 OCR 识别服务接口。 不同 OCR 厂商（腾讯、百度等）实现此接口，由 {@code LeagueImageOcrManager} 综合调用。
 *
 * 返回的表格行：每行为单元格文本数组，行数和列数取决于 OCR 识别结果。OCR 失败抛异常，由调用方决定降级处理。
 */
public interface LeagueImageOcrService {

	/**
	 * 对单张图片执行 OCR 表格识别，返回识别出的表格行（每行为单元格文本数组）。 识别失败抛异常，由调用方决定降级处理。
	 */
	List<String[]> ocrToRows(MultipartFile file) throws Exception;

}
