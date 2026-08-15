package com.tencent.wxcloudrun.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CompleteExchangeRequest implements Serializable {

	private String groupNo;
	private Long selfMemberId;
	private Long oppMemberId;
	private List<ExchangePair> pairs;

	@Data
	public static class ExchangePair implements Serializable {
		private String giveCategory;
		private String giveCardName;
		private String gainCategory;
		private String gainCardName;
	}

}
