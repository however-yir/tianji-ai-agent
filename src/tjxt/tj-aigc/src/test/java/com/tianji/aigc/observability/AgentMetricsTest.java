package com.tianji.aigc.observability;

import com.tianji.aigc.harness.ActionPolicyDecision;
import com.tianji.aigc.harness.AgentAction;
import com.tianji.aigc.harness.AgentObservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private AgentObservation observation(boolean allowed, boolean success) {
        AgentAction action = new AgentAction(
                "course.query", "CONSULT", "CourseTools.queryCourseById",
                "request-metrics", "session-metrics", 10001L, Map.of("courseId", 1L));
        ActionPolicyDecision decision = allowed
                ? ActionPolicyDecision.allow("ok")
                : ActionPolicyDecision.deny("denied");
        return AgentObservation.of(action, decision, success,
                "courseInfo", Map.of("name", "Java"), null, 12L);
    }

    @Test
    void shouldRecordRouteCountersAndHandoff() {
        AgentMetrics metrics = new AgentMetrics(registry);

        metrics.recordRoute("RECOMMEND");
        metrics.recordRoute("HUMAN_HANDOFF");

        assertThat(registry.counter("agent.route.total", "agent", "RECOMMEND").count()).isEqualTo(1);
        assertThat(registry.counter("agent.route.total", "agent", "HUMAN_HANDOFF").count()).isEqualTo(1);
        assertThat(registry.counter("agent.route.handoff").count()).isEqualTo(1);
    }

    @Test
    void shouldSanitizeBlankRouteTags() {
        new AgentMetrics(registry).recordRoute(" ");

        assertThat(registry.counter("agent.route.total", "agent", "UNKNOWN").count()).isEqualTo(1);
    }

    @Test
    void shouldRecordDeniedActionCounter() {
        new AgentMetrics(registry).recordAction(observation(false, false));

        assertThat(registry.counter("agent.action.total", "action", "course.query", "status", "DENIED").count())
                .isEqualTo(1);
        assertThat(registry.counter("agent.action.denied", "action", "course.query").count()).isEqualTo(1);
        assertThat(registry.counter("agent.runtime.success", "action", "course.query").count()).isZero();
    }

    @Test
    void shouldRecordRuntimeSuccessAndLatency() {
        new AgentMetrics(registry).recordAction(observation(true, true));

        assertThat(registry.counter("agent.runtime.success", "action", "course.query").count()).isEqualTo(1);
        assertThat(registry.counter("agent.runtime.failure", "action", "course.query").count()).isZero();
        assertThat(registry.timer("agent.runtime.latency", "action", "course.query", "status", "SUCCESS").count())
                .isEqualTo(1);
    }

    @Test
    void shouldRecordRuntimeFailure() {
        new AgentMetrics(registry).recordAction(observation(true, false));

        assertThat(registry.counter("agent.runtime.failure", "action", "course.query").count()).isEqualTo(1);
    }

    @Test
    void shouldIgnoreNullObservation() {
        new AgentMetrics(registry).recordAction(null);

        assertThat(registry.counter("agent.action.total").count()).isZero();
    }

    @Test
    void shouldCountSseSessionOutcome() {
        AgentMetrics metrics = new AgentMetrics(registry);

        metrics.recordSseSession(false);
        metrics.recordSseSession(true);

        assertThat(registry.counter("sse.session.completed").count()).isEqualTo(1);
        assertThat(registry.counter("sse.session.failed").count()).isEqualTo(1);
    }

    @Test
    void noopShouldNotDependOnRegistry() {
        AgentMetrics noop = AgentMetrics.noop();

        noop.recordRoute("RECOMMEND");
        noop.recordAction(observation(true, true));
        noop.recordSseSession(false);
    }
}
