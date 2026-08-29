package com.tianji.aigc.harness;

import com.tianji.aigc.observability.AgentMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class AgentHarnessService {

    private final ActionPolicyGuard policyGuard;
    private final AgentRuntime agentRuntime;
    private final HarnessEventRecorder eventRecorder;
    private final AgentMetrics agentMetrics;
    private final ConcurrentMap<String, AgentObservation> previewObservations = new ConcurrentHashMap<>();

    @Autowired
    public AgentHarnessService(ActionPolicyGuard policyGuard, AgentRuntime agentRuntime,
                               HarnessEventRecorder eventRecorder, AgentMetrics agentMetrics) {
        this.policyGuard = policyGuard;
        this.agentRuntime = agentRuntime;
        this.eventRecorder = eventRecorder;
        this.agentMetrics = agentMetrics;
    }

    public AgentHarnessService(ActionPolicyGuard policyGuard, AgentRuntime agentRuntime,
                               HarnessEventRecorder eventRecorder) {
        this(policyGuard, agentRuntime, eventRecorder, AgentMetrics.noop());
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
            return previewObservations.computeIfAbsent(action.idempotencyKey(),
                    ignored -> executeAllowedAction(action, decision, startTime));
        }
        return executeAllowedAction(action, decision, startTime);
    }

    private AgentObservation executeAllowedAction(AgentAction action, ActionPolicyDecision decision,
                                                   long startTime) {
        try {
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
