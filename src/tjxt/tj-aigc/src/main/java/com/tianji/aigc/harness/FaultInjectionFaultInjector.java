package com.tianji.aigc.harness;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * Profile-scoped injector that faithfully reproduces downstream failure classes:
 * timeouts, HTTP 500s, malformed tool results and slow responses. Enabled only under
 * the "fault-injection" profile (acceptance/demo), never in production.
 */
@Component
@Profile("fault-injection")
public class FaultInjectionFaultInjector implements FaultInjector {

    static final String COURSE_TIMEOUT = "course_timeout";
    static final String ORDER_TIMEOUT = "order_timeout";
    static final String RAG_TIMEOUT = "rag_timeout";
    static final String SERVICE_500 = "http_500";
    static final String MALFORMED_TOOL_RESPONSE = "malformed_tool_response";
    static final String SLOW_RESPONSE = "slow_response";

    private static final Map<String, Set<String>> ACTION_SCENARIOS = Map.of(
            COURSE_TIMEOUT, Set.of("course.query", "course.search"),
            ORDER_TIMEOUT, Set.of("order.preview"),
            RAG_TIMEOUT, Set.of("knowledgeops.rag_query")
    );
    private static final Set<String> GLOBAL_SCENARIOS = Set.of(
            SERVICE_500, MALFORMED_TOOL_RESPONSE, SLOW_RESPONSE
    );

    private final String scenario;
    private final long slowResponseMillis;

    public FaultInjectionFaultInjector(FaultInjectionProperties properties) {
        this.scenario = properties.getScenario();
        this.slowResponseMillis = properties.getSlowResponseMillis();
    }

    @Override
    public void beforeAction(AgentAction action) {
        if (!StringUtils.hasText(scenario)) {
            return;
        }
        Set<String> scoped = ACTION_SCENARIOS.get(scenario);
        if (scoped != null) {
            if (scoped.contains(action.actionType())) {
                throw new RuntimeException(
                        "fault-injection: " + action.actionType() + " timed out (simulated)");
            }
            return;
        }
        switch (scenario) {
            case SERVICE_500 -> throw new IllegalStateException(
                    "fault-injection: downstream returned HTTP 500 (simulated)");
            case MALFORMED_TOOL_RESPONSE -> throw new RuntimeException(
                    "fault-injection: malformed tool response (simulated)");
            case SLOW_RESPONSE -> sleep();
            default -> {
                // unknown scenario: no-op
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(slowResponseMillis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
