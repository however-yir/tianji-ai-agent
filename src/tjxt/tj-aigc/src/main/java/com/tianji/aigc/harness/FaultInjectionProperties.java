package com.tianji.aigc.harness;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Scenario selection for the fault-injection profile.
 * Values: course_timeout | order_timeout | rag_timeout | http_500 |
 * malformed_tool_response | slow_response | (empty = disabled)
 */
@Configuration
@ConfigurationProperties(prefix = "tj.ai.fault-injection")
public class FaultInjectionProperties {

    private String scenario = "";

    /** Delay added by the slow_response scenario, in milliseconds. */
    private long slowResponseMillis = 300;

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public long getSlowResponseMillis() {
        return slowResponseMillis;
    }

    public void setSlowResponseMillis(long slowResponseMillis) {
        this.slowResponseMillis = slowResponseMillis;
    }
}
