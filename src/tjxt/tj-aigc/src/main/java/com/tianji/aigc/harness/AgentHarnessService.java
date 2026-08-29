package com.tianji.aigc.harness;

import com.tianji.aigc.observability.AgentMetrics;
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

    @Autowired
    public AgentHarnessService(ActionPolicyGuard policyGuard, AgentRuntime agentRuntime,
                               HarnessEventRecorder eventRecorder, AgentMetrics agentMetrics,
                               IdempotencyStore idempotencyStore) {
        this(policyGuard, agentRuntime, eventRecorder, agentMetrics, idempotencyStore, new NoopFaultInjector());
    }

    public AgentHarnessService(ActionPolicyGuard policyGuard, AgentRuntime agentRuntime,
                               HarnessEventRecorder eventRecorder, AgentMetrics agentMetrics,
                               IdempotencyStore idempotencyStore, FaultInjector faultInjector) {
        this.policyGuard = policyGuard;
        this.agentRuntime = agentRuntime;
        this.eventRecorder = eventRecorder;
        this.agentMetrics = agentMetrics;
        this.idempotencyStore = idempotencyStore;
        this.faultInjector = faultInjector;
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
            AgentObservation observation = AgentObservation.of(
                    action, decision, false, null, null, decision.reason(), elapsed(startTime));
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
            faultInjector.beforeAction(action);
            Object result = agentRuntime.execute(action);
            String resultField = action.schema().map(schema -> schema.resultField(action)).orElse(null);
            AgentObservation observation = AgentObservation.of(
                    action, decision, result != null, resultField, result, null, elapsed(startTime));
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

    private void record(AgentObservation observation) {
        eventRecorder.record(observation);
        agentMetrics.recordAction(observation);
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
