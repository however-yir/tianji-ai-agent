package com.tianji.aigc.harness;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Default passthrough; the real fault scenarios are wired by the fault-injection profile. */
@Component
@Profile("!fault-injection")
public class NoopFaultInjector implements FaultInjector {

    @Override
    public void beforeAction(AgentAction action) {
        // no-op
    }
}
