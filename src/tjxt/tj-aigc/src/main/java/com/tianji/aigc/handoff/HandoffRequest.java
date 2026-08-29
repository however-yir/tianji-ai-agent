package com.tianji.aigc.handoff;

import java.time.Instant;

/**
 * A minimal but real human-handoff contract. The summary is redacted before storage;
 * the request object itself never contains secrets, tokens or private credentials.
 */
public record HandoffRequest(String handoffId,
                             String sessionId,
                             String traceId,
                             String reason,
                             String riskLevel,
                             String sourceAgent,
                             String summary,
                             Instant createdAt,
                             HandoffStatus status) {
}
