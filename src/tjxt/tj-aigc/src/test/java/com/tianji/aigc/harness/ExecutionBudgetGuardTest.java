package com.tianji.aigc.harness;

import com.tianji.aigc.budget.ExecutionBudgetProperties;
import com.tianji.aigc.budget.ExecutionBudgetService;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.knowledgeops.KnowledgeOpsClient;
import com.tianji.aigc.observability.AgentMetrics;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.trade.TradeClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionBudgetGuardTest {

    @Mock
    private CourseClient courseClient;
    @Mock
    private TradeClient tradeClient;
    @Mock
    private KnowledgeOpsClient knowledgeOpsClient;

    @AfterEach
    void tearDown() {
        ToolResultHolder.remove("request-budget-1");
        ToolResultHolder.remove("request-budget-2");
        ToolResultHolder.remove("request-budget-loop");
    }

    private ExecutionBudgetProperties settings() {
        ExecutionBudgetProperties p = new ExecutionBudgetProperties();
        p.setMaxToolCalls(2);
        p.setMaxSameAction(2);
        p.setMaxModelCalls(4);
        return p;
    }

    private ExecutionBudgetProperties strictSameActionSettings() {
        ExecutionBudgetProperties p = settings();
        p.setMaxSameAction(1);
        return p;
    }

    private AgentHarnessService harness(ExecutionBudgetService service) {
        return new AgentHarnessService(
                new ActionPolicyGuard(),
                new AgentRuntime(courseClient, tradeClient, knowledgeOpsClient),
                new HarnessEventRecorder(),
                AgentMetrics.noop(),
                new InMemoryIdempotencyStore(),
                new NoopFaultInjector(),
                service);
    }

    private AgentAction query(String requestId, String actionId) {
        return query(requestId, actionId, 1L);
    }

    private AgentAction query(String requestId, String actionId, long courseId) {
        return new AgentAction(
                "course.query", "CONSULT", "CourseTools.queryCourseById",
                requestId, actionId, "session-1", 10001L, Map.of("courseId", courseId));
    }

    @Test
    void shouldDenyToolCallWhenBudgetExceededAndNotReachRuntime() {
        com.tianji.api.dto.course.CourseBaseInfoDTO dto = new com.tianji.api.dto.course.CourseBaseInfoDTO();
        dto.setId(1L);
        when(courseClient.baseInfo(1L, true)).thenReturn(dto);
        when(courseClient.baseInfo(2L, true)).thenReturn(dto);
        ExecutionBudgetService service = new ExecutionBudgetService(settings());
        AgentHarnessService harness = harness(service);
        service.start("request-budget-1");

        harness.execute(query("request-budget-1", "a1", 1L));
        harness.execute(query("request-budget-1", "a2", 2L));
        AgentObservation third = harness.execute(query("request-budget-1", "a3", 3L));

        assertThat(third.allowed()).isFalse();
        assertThat(third.errorMessage()).contains("工具调用次数超出预算");
        assertThat(third.toTraceMap()).containsEntry("reasonCode", "TOO_MANY_TOOL_CALLS");
        verify(courseClient, times(2)).baseInfo(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldBlockRepeatedSameActionViaLoopDetection() {
        ExecutionBudgetService service = new ExecutionBudgetService(strictSameActionSettings());
        AgentHarnessService harness = harness(service);
        service.start("request-budget-loop");

        harness.execute(query("request-budget-loop", "l1"));
        AgentObservation repeated = harness.execute(query("request-budget-loop", "l1"));

        assertThat(repeated.allowed()).isFalse();
        assertThat(repeated.toTraceMap()).containsEntry("reasonCode", "DUPLICATE_ACTION");
        verify(courseClient, times(1)).baseInfo(1L, true);
    }

    @Test
    void budgetCannotBypassPolicyGuard() {
        ExecutionBudgetService service = new ExecutionBudgetService(settings());
        AgentHarnessService harness = harness(service);
        service.start("request-budget-1");

        AgentAction payment = new AgentAction(
                "payment.execute", "BUY", "UnregisteredTool",
                "request-budget-1", "p1", "session-1", 10001L, Map.of());

        AgentObservation observation = harness.execute(payment);

        assertThat(observation.allowed()).isFalse();
        // policy reason wins, budget must not let a dangerous action through
        assertThat(observation.errorMessage()).contains("未授权动作");
        verify(tradeClient, times(0)).prePlaceOrder(List.of());
    }

    @Test
    void budgetStateIsIsolatedPerRequest() {
        com.tianji.api.dto.course.CourseBaseInfoDTO dto = new com.tianji.api.dto.course.CourseBaseInfoDTO();
        dto.setId(1L);
        when(courseClient.baseInfo(1L, true)).thenReturn(dto);
        when(courseClient.baseInfo(2L, true)).thenReturn(dto);
        ExecutionBudgetService service = new ExecutionBudgetService(settings());
        AgentHarnessService harness = harness(service);
        service.start("request-budget-1");
        service.start("request-budget-2");

        // request 1 exhausts its 2 tool calls
        harness.execute(query("request-budget-1", "a1"));
        harness.execute(query("request-budget-1", "a2"));
        // request 2 still has a fresh budget
        AgentObservation fresh = harness.execute(query("request-budget-2", "b1", 2L));

        assertThat(fresh.success()).isTrue();
        verify(courseClient, times(1)).baseInfo(2L, true);
    }
}
