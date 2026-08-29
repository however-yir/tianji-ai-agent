package com.tianji.aigc.run;

import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory run repository with lazy 30-day retention. Acceptable for local demo and
 * tests; a JDBC-backed repository would slot in behind the same interface.
 */
@Repository
public class InMemoryAgentRunRepository implements AgentRunRepository {

    private static final Duration DEFAULT_RETENTION = Duration.ofDays(30);

    private final Duration retention;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public InMemoryAgentRunRepository() {
        this(DEFAULT_RETENTION);
    }

    public InMemoryAgentRunRepository(Duration retention) {
        this.retention = retention;
    }

    private record Entry(AgentRunRecord record, Instant expiresAt) {
    }

    @Override
    public void save(AgentRunRecord record) {
        if (record == null || record.runId() == null) {
            return;
        }
        entries.put(record.runId(), new Entry(record, Instant.now().plus(retention)));
    }

    @Override
    public Optional<AgentRunRecord> find(String runId) {
        Entry entry = entries.get(runId);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            entries.remove(runId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.record());
    }
}
