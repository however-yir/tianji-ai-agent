package com.tianji.aigc.harness;

import cn.hutool.core.collection.CollStreamUtil;
import com.tianji.aigc.knowledgeops.KnowledgeOpsClient;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.aigc.tools.result.PrePlaceOrder;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.trade.TradeClient;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private final CourseClient courseClient;
    private final TradeClient tradeClient;
    private final KnowledgeOpsClient knowledgeOpsClient;

    public Object execute(AgentAction action) {
        ActionSchema schema = action.schema()
                .orElseThrow(() -> new IllegalArgumentException("未知动作 schema: " + action.actionType()));
        return switch (schema) {
            case COURSE_QUERY -> queryCourse(action);
            case ORDER_PREVIEW -> previewOrder(action);
            case KNOWLEDGEOPS_RAG_QUERY -> ragQuery(action);
            case COURSE_SEARCH, MCP_CALL -> Map.of(
                    "status", "SCHEMA_ONLY",
                    "actionType", schema.getActionType(),
                    "description", schema.getDescription()
            );
        };
    }

    private CourseInfo queryCourse(AgentAction action) {
        Long courseId = ((Number) action.input().get("courseId")).longValue();
        return CourseInfo.of(courseClient.baseInfo(courseId, true));
    }

    private PrePlaceOrder previewOrder(AgentAction action) {
        if (action.userId() != null) {
            UserContext.setUser(action.userId());
        }
        List<Long> courseIds = toLongList(action.input().get("courseIds"));
        var orderConfirmVO = tradeClient.prePlaceOrder(courseIds);
        return orderConfirmVO == null ? null : PrePlaceOrder.of(orderConfirmVO);
    }

    private Map<String, Object> ragQuery(AgentAction action) {
        String query = String.valueOf(action.input().get("query"));
        String tenantId = String.valueOf(action.input().getOrDefault("tenantId", "default"));
        String chatId = String.valueOf(action.input().getOrDefault("chatId", action.sessionId()));
        return knowledgeOpsClient.ragSearch(query, tenantId, chatId);
    }

    private List<Long> toLongList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return CollStreamUtil.toList(collection, item -> ((Number) item).longValue());
    }
}
