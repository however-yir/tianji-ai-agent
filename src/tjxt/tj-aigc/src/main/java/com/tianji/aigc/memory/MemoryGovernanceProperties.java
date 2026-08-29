package com.tianji.aigc.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Conversation memory governance: bounded history, TTL and sensitive redaction.
 */
@Configuration
@ConfigurationProperties(prefix = "tj.ai.memory")
public class MemoryGovernanceProperties {

    /** Maximum recent turns kept per session (older turns are trimmed from Redis). */
    private int maxTurns = 20;

    /** Rolling TTL for session memory. */
    private int ttlDays = 30;

    public int getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    public int getTtlDays() {
        return ttlDays;
    }

    public void setTtlDays(int ttlDays) {
        this.ttlDays = ttlDays;
    }
}
