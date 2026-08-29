package com.tianji.aigc.reason;

/**
 * Stable machine-readable reason codes for policy / budget / runtime decisions.
 * Free-text reasons remain for humans; these codes drive metrics and UI mapping.
 */
public enum ReasonCode {
    // policy
    PAYMENT_NOT_ALLOWED,
    PRIVILEGED_ACTION,
    UNKNOWN_ACTION,
    VALIDATION_FAILED,
    AUTH_CONTEXT_MISSING,
    // routing
    LOW_CONFIDENCE,
    HIGH_RISK,
    ROUTE_FALLBACK,
    // budget / loop
    BUDGET_EXCEEDED,
    TOO_MANY_MODEL_CALLS,
    TOO_MANY_TOOL_CALLS,
    DUPLICATE_ACTION,
    ACTION_LOOP,
    // runtime
    TOOL_TIMEOUT,
    MODEL_TIMEOUT,
    RUNTIME_FAILURE,
    STORE_UNAVAILABLE,
    USER_CANCELLED,
    // misc
    NOT_APPLICABLE,
    UNSPECIFIED
}
