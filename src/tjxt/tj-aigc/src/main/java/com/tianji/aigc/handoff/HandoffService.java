package com.tianji.aigc.handoff;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and serves handoff requests. Storage is an in-memory contract holder for now;
 * the interface shape is deliberately adapter-ready: swapping in a ticketing/queue
 * integration only needs to persist and ACK/RESOLVE these records elsewhere.
 * External contact-center integration is NOT included.
 */
@Service
public class HandoffService {

    private static final Duration TTL = Duration.ofDays(7);

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private record Entry(HandoffRequest request, Instant expiresAt) {
    }

    public HandoffRequest create(String sessionId, String traceId, String sourceAgent,
                                 String reason, String riskLevel, String rawSummary) {
        String handoffId = "HF-" + UUID.randomUUID().toString().substring(0, 12);
        HandoffRequest request = new HandoffRequest(
                handoffId,
                sessionId,
                traceId,
                reason,
                riskLevel,
                sourceAgent,
                HandoffRedaction.redact(rawSummary),
                Instant.now(),
                HandoffStatus.REQUESTED
        );
        entries.put(handoffId, new Entry(request, Instant.now().plus(TTL)));
        return request;
    }

    public Optional<HandoffRequest> find(String handoffId) {
        Entry entry = entries.get(handoffId);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            entries.remove(handoffId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.request());
    }
}
