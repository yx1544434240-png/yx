package com.agedcare;

import com.agedcare.common.config.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

public class JacksonConfigTest {

    static class TimeDTO {
        private LocalDateTime time;

        public LocalDateTime getTime() {
            return time;
        }

        public void setTime(LocalDateTime time) {
            this.time = time;
        }
    }

    @Test
    public void testFlexibleLocalDateTimeParsing() throws Exception {
        JacksonConfig config = new JacksonConfig();
        Jackson2ObjectMapperBuilderCustomizer customizer = config.jackson2ObjectMapperBuilderCustomizer();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        customizer.customize(builder);
        ObjectMapper mapper = builder.build();

        // 1. 测试微信小程序发送的 "yyyy-MM-dd HH:mm:ss" 格式
        String jsonWithSpace = "{\"time\":\"2026-10-20 14:30:00\"}";
        TimeDTO dto1 = mapper.readValue(jsonWithSpace, TimeDTO.class);
        Assertions.assertNotNull(dto1.getTime());
        Assertions.assertEquals(2026, dto1.getTime().getYear());
        Assertions.assertEquals(10, dto1.getTime().getMonthValue());
        Assertions.assertEquals(20, dto1.getTime().getDayOfMonth());
        Assertions.assertEquals(14, dto1.getTime().getHour());
        Assertions.assertEquals(30, dto1.getTime().getMinute());

        // 2. 测试 ISO-8601 格式
        String jsonIso = "{\"time\":\"2026-10-20T14:30:00\"}";
        TimeDTO dto2 = mapper.readValue(jsonIso, TimeDTO.class);
        Assertions.assertNotNull(dto2.getTime());
        Assertions.assertEquals(dto1.getTime(), dto2.getTime());

        // 3. 测试序列化输出为统一标准字符串 "yyyy-MM-dd HH:mm:ss"
        String serialized = mapper.writeValueAsString(dto1);
        Assertions.assertTrue(serialized.contains("2026-10-20 14:30:00"));
    }
}
