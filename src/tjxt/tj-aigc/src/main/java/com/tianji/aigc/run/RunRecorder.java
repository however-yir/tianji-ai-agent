package com.tianji.aigc.run;

import com.tianji.aigc.budget.BudgetState;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.harness.AgentObservation;
import com.tianji.aigc.prompt.PromptResolution;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Collects per-request run evidence in the request-scoped holder and persists the final
 * record on the terminal path. All content is metadata; no chat payload is stored.
 */
@Component
public class RunRecorder {

    public static final String RUN_FIELD = "agentRunBuilder";

    public static final String RUN_ID_FIELD = "agentRunId";

    private final AgentRunRepository repository;

    private final CostEstimator costEstimator;

    public RunRecorder(AgentRunRepository repository, CostEstimator costEstimator) {
        this.repository = repository;
        this.costEstimator = costEstimator;
    }

    public String begin(String sessionId) {
        Object existing = ToolResultHolder.get(sessionId, RUN_ID_FIELD);
        if (existing instanceof String runId) {
            return runId;
        }
        String runId = "run-" + UUID.randomUUID().toString().substring(0, 12);
        AgentRunRecord.Builder builder = AgentRunRecord.builder(runId, runId, sessionId);
        ToolResultHolder.put(sessionId, RUN_FIELD, builder);
        ToolResultHolder.put(sessionId, RUN_ID_FIELD, runId);
        return runId;
    }

    public AgentRunRecord.Builder current(String sessionId) {
        Object builder = ToolResultHolder.get(sessionId, RUN_FIELD);
        return builder instanceof AgentRunRecord.Builder b ? b : null;
    }

    public void attachPrompt(String sessionId, PromptResolution resolution) {
        AttachmentHolder.attachPrompt(sessionId, resolution);
    }

    public void attachModel(String sessionId, String profile, String provider, String model) {
        AgentRunRecord.Builder builder = current(sessionId);
        if (builder != null) {
            builder.model(profile, provider, model);
        }
    }

    public void recordAction(String sessionId, AgentObservation observation) {
        AgentRunRecord.Builder builder = current(sessionId);
        if (builder != null) {
            Object reason = observation.toTraceMap().get("reasonCode");
            builder.recordAction(new ActionTraceEntry(
                    observation.actionType(),
                    observation.status(),
                    observation.latencyMillis(),
                    reason == null ? null : String.valueOf(reason)));
        }
    }

    public void finish(String sessionId, String terminalStatus, BudgetState budget, boolean handoff) {
        AgentRunRecord.Builder builder = current(sessionId);
        if (builder == null) {
            return;
        }
        PromptResolution prompt = AttachmentHolder.promptOf(sessionId);
        if (prompt != null) {
            builder.prompt(prompt.promptId(), prompt.version(), prompt.checksum(), prompt.overridden());
        }
        if (budget != null) {
            builder.modelCallCount(budget.modelCalls())
                    .toolCallCount(budget.toolCalls())
                    .policyDeniedCount(budget.policyDeniedCount())
                    .tokens(budget.inputTokens(), budget.outputTokens())
                    .budgetExceeded(budget.budgetExceeded());
            var cost = costEstimator.estimate(builder.provider(), builder.model(),
                    budget.inputTokens(), budget.outputTokens());
            if (cost.isPresent()) {
                builder.estimatedCost(cost.get().amount(), true);
            }
            else {
                builder.estimatedCost(null, false);
            }
        }
        Instant endTime = Instant.now();
        builder.endTime(endTime)
                .latencyMillis(Math.max(0, endTime.toEpochMilli() - builder.startTime().toEpochMilli()))
                .terminalStatus(terminalStatus)
                .handoff(handoff);
        repository.save(builder.build());
        ToolResultHolder.remove(sessionId);
    }

    public AgentRunRepository repository() {
        return repository;
    }

    /** Prompt metadata kept outside the builder so it can be attached before finish(). */
    private static final class AttachmentHolder {

        private AttachmentHolder() {
        }

        static void attachPrompt(String runId, PromptResolution resolution) {
            ToolResultHolder.put(runId, RUN_FIELD + ":prompt", resolution);
        }

        static PromptResolution promptOf(String runId) {
            Object value = ToolResultHolder.get(runId, RUN_FIELD + ":prompt");
            return value instanceof PromptResolution prompt ? prompt : null;
        }
    }
}
