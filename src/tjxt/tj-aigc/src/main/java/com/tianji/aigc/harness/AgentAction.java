package com.tianji.aigc.harness;

import java.util.Map;
import java.util.Optional;

public record AgentAction(String actionType,
                          String agentName,
                          String toolName,
                          String requestId,
                          String sessionId,
                          Long userId,
                          Map<String, Object> input) {

    public AgentAction {
        agentName = normalize(agentName, "UNKNOWN_AGENT");
        toolName = normalize(toolName, defaultToolName(actionType));
        input = input == null ? Map.of() : Map.copyOf(input);
    }

    public Optional<ActionSchema> schema() {
        return ActionSchema.resolve(actionType);
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
