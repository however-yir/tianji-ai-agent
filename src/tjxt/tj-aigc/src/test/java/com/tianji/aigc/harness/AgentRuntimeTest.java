package com.tianji.aigc.harness;

import com.tianji.aigc.knowledgeops.KnowledgeOpsClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.trade.TradeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRuntimeTest {

    @Mock
    private CourseClient courseClient;
    @Mock
    private TradeClient tradeClient;
    @Mock
    private KnowledgeOpsClient knowledgeOpsClient;

    private AgentRuntime newRuntime() {
        return new AgentRuntime(courseClient, tradeClient, knowledgeOpsClient);
    }

    @Test
    void shouldReturnSchemaOnlyResultForSearchAndMcpCall() {
        AgentAction search = new AgentAction(
                "course.search", "CONSULT", "CourseTools.search",
                "request-schema-only", "session-1", 10001L, Map.of("keyword", "java"));
        AgentAction mcp = new AgentAction(
                "mcp.call", "CONSULT", "McpRuntime.call",
                "request-mcp", "session-1", 10001L, Map.of("server", "demo", "tool", "echo"));

        Object searchResult = newRuntime().execute(search);
        Object mcpResult = newRuntime().execute(mcp);

        assertThat(searchResult).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) searchResult).get("status")).isEqualTo("SCHEMA_ONLY");
        assertThat(((Map<?, ?>) searchResult).get("actionType")).isEqualTo("course.search");
        assertThat(((Map<?, ?>) mcpResult).get("actionType")).isEqualTo("mcp.call");
        verifyNoInteractions(courseClient, tradeClient, knowledgeOpsClient);
    }

    @Test
    void shouldRouteRagQueryToKnowledgeOpsWithSessionChatId() {
        when(knowledgeOpsClient.ragSearch("java 泛型", "default", "session-rag"))
                .thenReturn(java.util.Map.<String, Object>of("sources", "x"));
        AgentAction rag = new AgentAction(
                "knowledgeops.rag_query", "KNOWLEDGE", "KnowledgeOpsClient.ragSearch",
                "request-rag", "session-rag", 10001L, Map.of("query", "java 泛型"));

        Object result = newRuntime().execute(rag);

        assertThat(result).isEqualTo(java.util.Map.<String, Object>of("sources", "x"));
        verify(knowledgeOpsClient).ragSearch("java 泛型", "default", "session-rag");
    }

    @Test
    void shouldHandleEmptyCourseIdsAndNullPreviewWithoutLeakingUser() {
        when(tradeClient.prePlaceOrder(java.util.List.of())).thenReturn(null);
        AgentAction action = new AgentAction(
                "order.preview", "BUY", "OrderTools.prePlaceOrder",
                "request-empty-preview", "session-1", 10001L, Map.of("courseIds", "not-a-list"));

        Object result = newRuntime().execute(action);

        assertThat(result).isNull();
        assertThat(com.tianji.common.utils.UserContext.getUser()).isNull();
    }
}
