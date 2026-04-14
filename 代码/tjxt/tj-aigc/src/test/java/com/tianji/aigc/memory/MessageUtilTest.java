package com.tianji.aigc.memory;

import cn.hutool.json.JSONUtil;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageUtilTest {

    @AfterEach
    void tearDown() {
        ToolResultHolder.remove("message-1");
        ToolResultHolder.remove("request-1");
    }

    @Test
    void shouldInlineToolResultParamsIntoAssistantHistoryPayload() {
        ToolResultHolder.put("message-1", Constant.REQUEST_ID, "request-1");
        ToolResultHolder.put("request-1", "courseInfo_101", Map.of("courseId", 101L));

        MyAssistantMessage message = new MyAssistantMessage(
                "assistant-answer",
                Map.of(Constant.ID, "message-1"),
                List.of()
        );

        String json = MessageUtil.toJson(message);
        RedisMessage redisMessage = JSONUtil.toBean(json, RedisMessage.class);

        assertThat(redisMessage.getParams())
                .containsEntry("courseInfo_101", Map.of("courseId", 101L));
        assertThat(ToolResultHolder.get("message-1")).isNull();
        assertThat(ToolResultHolder.get("request-1"))
                .containsEntry("courseInfo_101", Map.of("courseId", 101L));
    }
}
