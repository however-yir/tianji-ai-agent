package com.tianji.aigc.observability;

import com.tianji.aigc.harness.AgentObservation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Records low-cardinality business-agent metrics. Request, session and user identifiers
 * deliberately stay in traces rather than metric tags to avoid a cardinality or privacy leak.
 */
@Component
public class AgentMetrics {

    private final MeterRegistry meterRegistry;

    public AgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public static AgentMetrics noop() {
        return new AgentMetrics(null);
    }

    public void recordRoute(String nextAgent) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("agent.route.total", "agent", safeTag(nextAgent)).increment();
        if ("HUMAN_HANDOFF".equals(nextAgent)) {
            meterRegistry.counter("agent.route.handoff").increment();
        }
    }

    public void recordAction(AgentObservation observation) {
        if (meterRegistry == null || observation == null) {
            return;
        }
        meterRegistry.counter("agent.action.total",
                "action", safeTag(observation.actionType()),
                "status", observation.status()).increment();
        if (!observation.allowed()) {
            meterRegistry.counter("agent.action.denied", "action", safeTag(observation.actionType())).increment();
            return;
        }

        String outcomeMetric = observation.success() ? "agent.runtime.success" : "agent.runtime.failure";
        meterRegistry.counter(outcomeMetric, "action", safeTag(observation.actionType())).increment();
        Timer.builder("agent.runtime.latency")
                .tag("action", safeTag(observation.actionType()))
                .tag("status", observation.status())
                .register(meterRegistry)
                .record(observation.latencyMillis(), TimeUnit.MILLISECONDS);
    }

    public void recordSseSession(boolean failed) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(failed ? "sse.session.failed" : "sse.session.completed").increment();
    }

    private String safeTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
