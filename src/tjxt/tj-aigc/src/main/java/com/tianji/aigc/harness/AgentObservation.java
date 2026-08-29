package com.tianji.aigc.harness;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AgentObservation(String observationId,
                               String requestId,
                               String traceId,
                               String sessionId,
                               String agentName,
                               String toolName,
                               String actionType,
                               boolean allowed,
                               boolean success,
                               String policyReason,
                               String resultField,
                               Object result,
                               String errorMessage,
                               String reasonCode,
                               long latencyMillis,
                               Instant observedAt) {

    public static AgentObservation of(AgentAction action, ActionPolicyDecision decision,
                                      boolean success, String resultField, Object result,
                                      String errorMessage, long latencyMillis) {
        return ofWithCode(action, decision, success, resultField, result, errorMessage, latencyMillis, null);
    }

    public static AgentObservation ofWithCode(AgentAction action, ActionPolicyDecision decision,
                                              boolean success, String resultField, Object result,
                                              String errorMessage, long latencyMillis, String reasonCode) {
        return new AgentObservation(
                UUID.randomUUID().toString(),
                action.requestId(),
                action.requestId(),
                action.sessionId(),
                action.agentName(),
                action.toolName(),
                action.actionType(),
                decision.allowed(),
                success,
                decision.reason(),
                resultField,
                result,
                errorMessage,
                reasonCode,
                latencyMillis,
                Instant.now()
        );
    }

    public String status() {
        if (!allowed) {
            return "DENIED";
        }
        return success ? "SUCCESS" : "FAILURE";
    }

    public <T> T resultAs(Class<T> resultType) {
        if (resultType == null || result == null || !resultType.isInstance(result)) {
            return null;
        }
        return resultType.cast(result);
    }

    public Map<String, Object> toTraceMap() {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("observationId", observationId);
        trace.put("requestId", requestId);
        trace.put("traceId", traceId);
        trace.put("sessionId", sessionId);
        trace.put("agentName", agentName);
        trace.put("toolName", toolName);
        trace.put("actionType", actionType);
        trace.put("status", status());
        trace.put("allowed", allowed);
        trace.put("success", success);
        trace.put("policyReason", policyReason);
        trace.put("resultField", resultField);
        trace.put("resultType", result == null ? null : result.getClass().getSimpleName());
        trace.put("resultSummary", result == null ? null : result.getClass().getSimpleName() + " available");
        trace.put("errorMessage", errorMessage);
        trace.put("latencyMillis", latencyMillis);
        trace.put("observedAt", observedAt.toString());
        if (reasonCode != null) {
            trace.put("reasonCode", reasonCode);
        }
        return trace;
    }
}
