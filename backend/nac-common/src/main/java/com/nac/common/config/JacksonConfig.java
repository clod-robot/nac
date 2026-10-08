package com.nac.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局时间序列化：所有 Date / LocalDateTime 统一输出为 yyyy-MM-dd HH:mm:ss（服务器本地时区）。
 * 作为全站时间展示的唯一入口，避免各模块/各页面零散格式化带来的偏差。
 */
@Configuration
public class JacksonConfig {

    public static final String PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(PATTERN);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer nacTimeCustomizer() {
        return builder -> {
            builder.simpleDateFormat(PATTERN);
            builder.serializers(new LocalDateTimeSerializer(FORMATTER));
            builder.deserializers(new LocalDateTimeDeserializer(FORMATTER));
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }

    public static String now() {
        return LocalDateTime.now().format(FORMATTER);
    }
}
