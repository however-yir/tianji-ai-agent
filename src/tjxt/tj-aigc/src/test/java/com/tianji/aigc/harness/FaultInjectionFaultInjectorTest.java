package com.tianji.aigc.harness;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaultInjectionFaultInjectorTest {

    private FaultInjectionProperties properties(String scenario, long slowMillis) {
        FaultInjectionProperties p = new FaultInjectionProperties();
        p.setScenario(scenario);
        p.setSlowResponseMillis(slowMillis);
        return p;
    }

    private AgentAction action(String actionType) {
        return new AgentAction(
                actionType, "CONSULT", "Tools.x", "request-fault", "session-fault", 10001L, Map.of());
    }

    @Test
    void shouldThrowTimeoutOnlyForScopedActions() {
        FaultInjectionFaultInjector injector = new FaultInjectionFaultInjector(properties("course_timeout", 300));

        assertThatThrownBy(() -> injector.beforeAction(action("course.query")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("timed out");
        assertThatCode(() -> injector.beforeAction(action("order.preview"))).doesNotThrowAnyException();
    }

    @Test
    void shouldThrowForGlobalHttp500AndMalformedScenarios() {
        FaultInjectionFaultInjector http500 = new FaultInjectionFaultInjector(properties("http_500", 300));
        assertThatThrownBy(() -> http500.beforeAction(action("course.search")))
                .hasMessageContaining("HTTP 500");

        FaultInjectionFaultInjector malformed = new FaultInjectionFaultInjector(properties("malformed_tool_response", 300));
        assertThatThrownBy(() -> malformed.beforeAction(action("knowledgeops.rag_query")))
                .hasMessageContaining("malformed");
    }

    @Test
    void shouldSleepOnSlowResponseScenario() {
        FaultInjectionFaultInjector injector = new FaultInjectionFaultInjector(properties("slow_response", 60));

        long started = System.currentTimeMillis();
        assertThatCode(() -> injector.beforeAction(action("course.query"))).doesNotThrowAnyException();
        assertThat(Long.valueOf(System.currentTimeMillis() - started)).isGreaterThanOrEqualTo(50L);
    }

    @Test
    void shouldBeNoopWhenScenarioBlankOrUnknown() {
        FaultInjectionFaultInjector blank = new FaultInjectionFaultInjector(properties("", 300));
        assertThatCode(() -> blank.beforeAction(action("order.preview"))).doesNotThrowAnyException();

        FaultInjectionFaultInjector unknown = new FaultInjectionFaultInjector(properties("unknown_scenario", 300));
        assertThatCode(() -> unknown.beforeAction(action("course.query"))).doesNotThrowAnyException();
    }

    @Test
    void shouldCoverOrderAndRagScopedScenarios() {
        FaultInjectionFaultInjector order = new FaultInjectionFaultInjector(properties("order_timeout", 300));
        assertThatThrownBy(() -> order.beforeAction(action("order.preview"))).hasMessageContaining("timed out");

        FaultInjectionFaultInjector rag = new FaultInjectionFaultInjector(properties("rag_timeout", 300));
        assertThatThrownBy(() -> rag.beforeAction(action("knowledgeops.rag_query"))).hasMessageContaining("timed out");
    }
}
