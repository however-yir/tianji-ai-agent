package com.tianji.aigc.run;

import java.util.Optional;

/**
 * Persistence boundary for run metadata. Only metadata, never chat content.
 */
public interface AgentRunRepository {

    void save(AgentRunRecord record);

    Optional<AgentRunRecord> find(String runId);
}
