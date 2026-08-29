package com.tianji.aigc.harness;

import com.tianji.aigc.budget.BudgetDecision;
import com.tianji.aigc.budget.BudgetState;
import com.tianji.aigc.budget.ExecutionBudgetProperties;
import com.tianji.aigc.budget.ExecutionBudgetService;
import com.tianji.aigc.observability.AgentMetrics;
import com.tianji.aigc.run.RunRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentHarnessService {

    /** In-flight claim guard for order.preview; result cache TTL for duplicates. */
    private static final long CLAIM_TTL_MILLIS = 30_000L;
    private static final long RESULT_TTL_MILLIS = 600_000L;

    private final ActionPolicyGuard policyGuard;
    private final AgentRuntime agentRuntime;
    private final HarnessEventRecorder eventRecorder;
    private final AgentMetrics agentMetrics;
    private final IdempotencyStore idempotencyStore;
    private final FaultInjector faultInjector;
    private final ExecutionBudgetService budgetService;
    private final RunRecorder runRecorder;

    @Autowired
    public AgentHarnessService(ActionPolicyGuard policyGuard, AgentRuntime agentRuntime,
                               HarnessEventRecorder eventRecorder, AgentMetrics agentMetrics,
                               IdempotencyStore idempotencyStore) {
        this(policyGuard, agentRuntime, eventRecorder, agentMetrics, idempotencyStore,
                new NoopFaultInjector(), new ExecutionBudgetService(new ExecutionBudgetProperties()),
                null);
    }

    public AgentHarnessService(ActionPolicyGuard policyGuard, AgentRuntime agentRuntime,
                               HarnessEventRecorder eventRecorder, AgentMetrics agentMetrics,
                               IdempotencyStore idempotencyStore, FaultInjector faultInjector) {
        this(policyGuard, agentRuntime, eventRecorder, agentMetrics, idempotencyStore,
                faultInjector, new ExecutionBudgetService(new ExecutionBudgetProperties()), null);
    }

    public AgentHarnessService(ActionPolicyGuard policyGuard, AgentRuntime agentRuntime,
                               HarnessEventRecorder eventRecorder, AgentMetrics agentMetrics,
                               IdempotencyStore idempotencyStore, FaultInjector faultInjector,
                               ExecutionBudgetService budgetService) {
        this(policyGuard, agentRuntime, eventRecorder, agentMetrics, idempotencyStore,
                faultInjector, budgetService, null);
    }

    public AgentHarnessService(ActionPolicyGuard policyGuard, AgentRuntime agentRuntime,
                               HarnessEventRecorder eventRecorder, AgentMetrics agentMetrics,
                               IdempotencyStore idempotencyStore, FaultInjector faultInjector,
                               ExecutionBudgetService budgetService, RunRecorder runRecorder) {
        this.policyGuard = policyGuard;
        this.agentRuntime = agentRuntime;
        this.eventRecorder = eventRecorder;
        this.agentMetrics = agentMetrics;
        this.idempotencyStore = idempotencyStore;
        this.faultInjector = faultInjector;
        this.budgetService = budgetService;
        this.runRecorder = runRecorder;
    }

    public AgentHarnessService(ActionPolicyGuard policyGuard, AgentRuntime agentRuntime,
                               HarnessEventRecorder eventRecorder, AgentMetrics agentMetrics) {
        this(policyGuard, agentRuntime, eventRecorder, agentMetrics, new InMemoryIdempotencyStore());
    }

    public AgentHarnessService(ActionPolicyGuard policyGuard, AgentRuntime agentRuntime,
                               HarnessEventRecorder eventRecorder) {
        this(policyGuard, agentRuntime, eventRecorder, AgentMetrics.noop(), new InMemoryIdempotencyStore());
    }

    public AgentObservation execute(AgentAction action) {
        long startTime = System.currentTimeMillis();
        ActionPolicyDecision decision = policyGuard.decide(action);
        if (!decision.allowed()) {
            budgetService.current(action.requestId()).ifPresent(BudgetState::recordPolicyDenied);
            AgentObservation observation = AgentObservation.ofWithCode(
                    action, decision, false, null, null, decision.reason(), elapsed(startTime),
                    decision.reasonCode());
            record(observation);
            return observation;
        }
        if (action.schema().orElse(null) == ActionSchema.ORDER_PREVIEW) {
            return executeIdempotent(action, decision, startTime);
        }
        return executeAllowedAction(action, decision, startTime);
    }

    /**
     * At most one caller executes the preview per (user, actionId) key; duplicates either
     * get the cached result or a deterministic "already processing" observation. A failure
     * releases the claim so the user can retry. If the store itself is unavailable the
     * action fails closed - the runtime tool is never invoked by a second executor.
     */
    private AgentObservation executeIdempotent(AgentAction action, ActionPolicyDecision decision,
                                               long startTime) {
        String key = action.idempotencyKey();
        boolean acquired;
        try {
            acquired = idempotencyStore.tryAcquire(key, CLAIM_TTL_MILLIS);
        }
        catch (RuntimeException storeFailure) {
            log.warn("[AgentHarness] 幂等存储不可用，拒绝重复执行, key={}: {}", key, storeFailure.getMessage());
            AgentObservation observation = AgentObservation.of(
                    action, decision, false, null, null,
                    "幂等存储不可用，本次请求被拒绝（避免重复执行）", elapsed(startTime));
            record(observation);
            return observation;
        }
        if (!acquired) {
            // Duplicate in-flight or already completed: no second execution.
            return idempotencyStore.get(key).orElseGet(() -> AgentObservation.of(
                    action, decision, false, null, null,
                    "相同请求正在处理中，请稍后查看结果", elapsed(startTime)));
        }
        AgentObservation observation = executeAllowedAction(action, decision, startTime);
        if (observation.allowed() && observation.success()) {
            idempotencyStore.complete(key, observation, RESULT_TTL_MILLIS);
        }
        else {
            idempotencyStore.release(key);
        }
        return observation;
    }

    private AgentObservation executeAllowedAction(AgentAction action, ActionPolicyDecision decision,
                                                   long startTime) {
        try {
            BudgetDecision budgetDecision = checkBudget(action);
            if (!budgetDecision.allowed()) {
                // budget denial is a harness-level refusal: reflect it on allowed() too
                AgentObservation observation = AgentObservation.ofWithCode(
                        action, ActionPolicyDecision.deny(budgetDecision.message()), false,
                        null, null, budgetDecision.message(), elapsed(startTime),
                        budgetDecision.reasonCode().name());
                record(observation);
                return observation;
            }
            faultInjector.beforeAction(action);
            Object result = agentRuntime.execute(action);
            String resultField = action.schema().map(schema -> schema.resultField(action)).orElse(null);
            AgentObservation observation = AgentObservation.of(
                    action, decision, result != null, resultField, result, null, elapsed(startTime));
            budgetService.current(action.requestId()).ifPresent(budget -> budget.afterToolCall(elapsed(startTime)));
            record(observation);
            return observation;
        }
        catch (RuntimeException e) {
            // 不打日志的话，Feign/网络超时、模型 NPE 这类错误对运维不可见。
            log.warn("[AgentHarness] actionType={} 执行失败: {}", action.actionType(), e.getMessage(), e);
            AgentObservation observation = AgentObservation.of(
                    action, decision, false, null, null, e.getMessage(), elapsed(startTime));
            record(observation);
            return observation;
        }
    }

    private BudgetDecision checkBudget(AgentAction action) {
        return budgetService.current(action.requestId())
                .map(budget -> {
                    String signature = BudgetState.signature(action.actionType(),
                            normalizeArguments(action.input()));
                    BudgetDecision decision = budget.beforeAction(action.actionType(), signature);
                    return decision.allowed() ? budget.beforeToolCall() : decision;
                })
                .orElse(BudgetDecision.ok());
    }

    private String normalizeArguments(java.util.Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        return new java.util.TreeMap<>(input).toString();
    }

    private void record(AgentObservation observation) {
        eventRecorder.record(observation);
        agentMetrics.recordAction(observation);
        if (runRecorder != null) {
            runRecorder.recordAction(observation.sessionId(), observation);
        }
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
