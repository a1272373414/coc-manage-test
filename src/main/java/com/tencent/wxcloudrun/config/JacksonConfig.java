package com.tencent.wxcloudrun.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局配置： 统一 LocalDateTime 序列化/反序列化格式为 "yyyy-MM-dd HH:mm:ss"， 使前端 el-date-picker
 * 传入的空格分隔日期字符串能被正确解析。
 */
@Configuration
public class JacksonConfig {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Bean
	public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
		return builder -> {
			builder.serializers(new LocalDateTimeSerializer(FORMATTER));
			builder.deserializers(new LocalDateTimeDeserializer(FORMATTER));
		};
	}

}
