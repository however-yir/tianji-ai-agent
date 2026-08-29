package com.tianji.aigc.run;

import com.tianji.aigc.budget.ExecutionBudgetProperties;
import com.tianji.aigc.budget.BudgetState;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.harness.ActionPolicyDecision;
import com.tianji.aigc.harness.AgentAction;
import com.tianji.aigc.harness.AgentObservation;
import com.tianji.aigc.prompt.PromptRegistry;
import com.tianji.aigc.prompt.PromptResolution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunRecorderTest {

    private final InMemoryAgentRunRepository repository = new InMemoryAgentRunRepository(Duration.ofDays(30));
    private final CostEstimator estimator = new CostEstimator(new ModelPricingProperties());
    private final RunRecorder recorder = new RunRecorder(repository, estimator);

    @AfterEach
    void tearDown() {
        ToolResultHolder.remove("session-run-1");
    }

    private AgentObservation observation() {
        AgentAction action = new AgentAction(
                "course.query", "CONSULT", "CourseTools.queryCourseById",
                "trace-1", "req-1", "session-run-1", 10001L, Map.of("courseId", 1L));
        return AgentObservation.of(action, ActionPolicyDecision.allow("ok"), true,
                "courseInfo_1", Map.of("name", "Java"), null, 7L);
    }

    @Test
    void shouldPersistRunRecordWithPromptBudgetAndActions() {
        String runId = recorder.begin("session-run-1");

        PromptRegistry registry = new PromptRegistry();
        registry.init();
        PromptResolution resolution = registry.trace("consult", "consult prompt baseline content");
        recorder.attachPrompt("session-run-1", resolution);

        ExecutionBudgetProperties props = new ExecutionBudgetProperties();
        BudgetState budget = new BudgetState(props);
        budget.beforeModelCall();
        budget.beforeToolCall();
        budget.recordTokens(10, 20);
        recorder.recordAction("session-run-1", observation());

        recorder.finish("session-run-1", "SUCCESS", budget, false);

        AgentRunRecord record = repository.find(runId).orElseThrow();
        assertThat(record.runId()).isEqualTo(runId);
        assertThat(record.sessionId()).isEqualTo("session-run-1");
        assertThat(record.promptId()).isEqualTo("consult");
        assertThat(record.inputTokens()).isEqualTo(10);
        assertThat(record.outputTokens()).isEqualTo(20);
        assertThat(record.modelCallCount()).isEqualTo(1);
        assertThat(record.toolCallCount()).isEqualTo(1);
        assertThat(record.terminalStatus()).isEqualTo("SUCCESS");
        assertThat(record.costKnown()).isFalse(); // no pricing configured -> unavailable, not 0
        assertThat(record.actions()).hasSize(1);
        assertThat(record.actions().get(0).actionType()).isEqualTo("course.query");
        assertThat(record.actions().get(0).status()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldRecordCostWhenPricingConfigured() {
        ModelPricingProperties pricing = new ModelPricingProperties();
        ModelPricingProperties.Price price = new ModelPricingProperties.Price();
        ModelPricingProperties.ModelPrice model = new ModelPricingProperties.ModelPrice();
        model.setInputPerMillion(1.0);
        model.setOutputPerMillion(2.0);
        price.getModels().put("qwen-plus", model);
        pricing.getProviders().put("openai", price);
        RunRecorder pricedRecorder = new RunRecorder(repository, new CostEstimator(pricing));

        String runId = pricedRecorder.begin("session-run-1");
        pricedRecorder.attachModel("session-run-1", "general", "openai", "qwen-plus");
        BudgetState budget = new BudgetState(new ExecutionBudgetProperties());
        budget.recordTokens(1_000_000, 500_000);

        pricedRecorder.finish("session-run-1", "SUCCESS", budget, false);

        AgentRunRecord record = repository.find(runId).orElseThrow();
        assertThat(record.costKnown()).isTrue();
        // 1M input @1/M + 0.5M output @2/M = 1 + 1 = 2.0
        assertThat(record.estimatedCost()).isEqualTo(2.0);
    }
}
