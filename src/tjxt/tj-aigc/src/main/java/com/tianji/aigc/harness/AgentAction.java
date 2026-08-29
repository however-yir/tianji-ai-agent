package com.tianji.aigc.harness;

import java.util.Map;
import java.util.Optional;

public record AgentAction(String actionType,
                          String agentName,
                          String toolName,
                          String requestId,
                          String actionId,
                          String sessionId,
                          Long userId,
                          Map<String, Object> input) {

    public AgentAction {
        agentName = normalize(agentName, "UNKNOWN_AGENT");
        toolName = normalize(toolName, defaultToolName(actionType));
        requestId = normalize(requestId, "UNKNOWN_REQUEST");
        actionId = normalize(actionId, requestId);
        sessionId = normalize(sessionId, "UNKNOWN_SESSION");
        input = input == null ? Map.of() : Map.copyOf(input);
    }

    public AgentAction(String actionType, String agentName, String toolName, String requestId,
                       String sessionId, Long userId, Map<String, Object> input) {
        this(actionType, agentName, toolName, requestId, requestId, sessionId, userId, input);
    }

    public Optional<ActionSchema> schema() {
        return ActionSchema.resolve(actionType);
    }

    /**
     * `order.preview` is the only currently idempotent action. Scope the cache key to the
     * acting user so two users cannot accidentally share a preview result in one JVM.
     */
    public String idempotencyKey() {
        return actionType + ":" + (userId == null ? "anonymous" : userId) + ":" + actionId;
    }

    private static String defaultToolName(String actionType) {
        return ActionSchema.resolve(actionType)
                .map(ActionSchema::getDefaultToolName)
                .orElse("UNKNOWN_TOOL");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
