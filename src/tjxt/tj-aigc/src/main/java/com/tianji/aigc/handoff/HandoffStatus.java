package com.tianji.aigc.handoff;

/**
 * Lifecycle of a handoff request. The external contact-center adapter (not included)
 * is expected to transition REQUESTED -> ACKNOWLEDGED -> RESOLVED once integrated.
 */
public enum HandoffStatus {
    REQUESTED,
    ACKNOWLEDGED,
    RESOLVED
}
