package com.tianji.aigc.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultHolderTest {

    @AfterEach
    void tearDown() {
        ToolResultHolder.remove("request-1");
        ToolResultHolder.remove("request-2");
    }

    @Test
    void shouldMergeToolResultsForSameRequest() {
        ToolResultHolder.put("request-1", "courseInfo_101", Map.of("id", 101L));
        ToolResultHolder.putAll("request-1", Map.of("prePlaceOrder", Map.of("orderId", 9001L)));

        Map<String, Object> result = ToolResultHolder.get("request-1");

        assertThat(result)
                .containsKeys("courseInfo_101", "prePlaceOrder");
        assertThat(ToolResultHolder.get("request-1", "courseInfo_101")).isInstanceOf(Map.class);
    }

    @Test
    void shouldIgnoreBlankKeysAndEmptyPayloads() {
        ToolResultHolder.put(null, "field", "value");
        ToolResultHolder.put("request-2", null, "value");
        ToolResultHolder.putAll("request-2", Map.of());

        assertThat(ToolResultHolder.get((String) null)).isNull();
        assertThat(ToolResultHolder.get("request-2")).isNull();
    }

    @Test
    void shouldRemoveResultsByRequestId() {
        ToolResultHolder.put("request-1", "courseInfo_101", Map.of("id", 101L));

        ToolResultHolder.remove("request-1");

        assertThat(ToolResultHolder.get("request-1")).isNull();
    }
}
