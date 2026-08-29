package com.tianji.aigc.run;

/**
 * Auditable per-action entry: no payload, no raw user content, no hidden reasoning.
 */
public record ActionTraceEntry(String actionType, String status, long latencyMillis, String reasonCode) {
}
