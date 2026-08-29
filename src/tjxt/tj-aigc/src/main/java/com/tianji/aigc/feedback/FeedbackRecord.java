package com.tianji.aigc.feedback;

import java.time.Instant;

/**
 * Product feedback on an assistant message. Never stores prompt text, reasoning or
 * credentials; comments pass through redaction.
 */
public record FeedbackRecord(String feedbackId, String runId, String messageId,
                             String rating, String reasonCode, String comment, Instant createdAt) {
}
