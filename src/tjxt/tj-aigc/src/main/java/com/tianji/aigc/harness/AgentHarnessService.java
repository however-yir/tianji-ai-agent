package com.tianji.aigc.harness;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentHarnessService {

    private final ActionPolicyGuard policyGuard;
    private final AgentRuntime agentRuntime;
    private final HarnessEventRecorder eventRecorder;

    public AgentObservation execute(AgentAction action) {
        long startTime = System.currentTimeMillis();
        ActionPolicyDecision decision = policyGuard.decide(action);
        if (!decision.allowed()) {
            AgentObservation observation = AgentObservation.of(
                    action, decision, false, null, null, decision.reason(), elapsed(startTime));
            eventRecorder.record(observation);
            return observation;
        }
        try {
            Object result = agentRuntime.execute(action);
            String resultField = action.schema().map(schema -> schema.resultField(action)).orElse(null);
            AgentObservation observation = AgentObservation.of(
                    action, decision, result != null, resultField, result, null, elapsed(startTime));
            eventRecorder.record(observation);
            return observation;
        }
        catch (RuntimeException e) {
            AgentObservation observation = AgentObservation.of(
                    action, decision, false, null, null, e.getMessage(), elapsed(startTime));
            eventRecorder.record(observation);
            return observation;
        }
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
