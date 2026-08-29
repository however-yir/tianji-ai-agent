package com.tianji.aigc.harness;

/**
 * Deterministic fault injection hook, active only under the "fault-injection" profile.
 * Never enabled in production: it simulates downstream failures so reliability
 * behavior can be reproduced on demand (acceptance, demos, tests).
 */
public interface FaultInjector {

    /** Invoked before the runtime executes a tool action; may throw to simulate a downstream failure. */
    void beforeAction(AgentAction action);
}
