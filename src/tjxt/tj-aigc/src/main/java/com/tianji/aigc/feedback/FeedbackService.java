package com.tianji.aigc.feedback;

import com.tianji.aigc.handoff.HandoffRedaction;
import com.tianji.aigc.run.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Validates and stores feedback. Approved reasons are stable codes; comments are length
 * capped and redacted. Feedback is a candidate generator for human review, never an
 * automatic dataset entry.
 */
@Service
public class FeedbackService {

    public static final int MAX_COMMENT_LENGTH = 500;
    public static final Set<String> RATINGS = Set.of("UP", "DOWN");
    public static final Set<String> REASON_CODES = Set.of(
            "HELPFUL", "INCORRECT", "IRRELEVANT", "TOOL_ERROR", "TOO_VERBOSE", "UNSAFE", "OTHER");

    private final FeedbackRepository repository;
    private final AgentRunRepository runRepository;

    public FeedbackService(FeedbackRepository repository, AgentRunRepository runRepository) {
        this.repository = repository;
        this.runRepository = runRepository;
    }

    public FeedbackRecord submit(String runId, String messageId, String rating,
                                 String reasonCode, String comment) {
        if (!StringUtils.hasText(runId) || runRepository.find(runId).isEmpty()) {
            throw new IllegalArgumentException("runId 不存在: " + runId);
        }
        if (!StringUtils.hasText(messageId)) {
            throw new IllegalArgumentException("messageId 不能为空");
        }
        if (rating == null || !RATINGS.contains(rating)) {
            throw new IllegalArgumentException("不合法的 rating: " + rating);
        }
        if (reasonCode != null && !reasonCode.isBlank() && !REASON_CODES.contains(reasonCode)) {
            throw new IllegalArgumentException("不合法的 reasonCode: " + reasonCode);
        }
        String safeComment = comment == null ? "" : comment.trim();
        safeComment = StringUtils.hasText(safeComment)
                ? HandoffRedaction.redact(safeComment.substring(0, Math.min(safeComment.length(), MAX_COMMENT_LENGTH)))
                : "";
        String feedbackId = "fb-" + UUID.randomUUID().toString().substring(0, 12);
        // one feedback per message: same user + message updates the previous submission
        repository.upsertByMessage(new FeedbackRecord(
                feedbackId, runId, messageId, rating,
                reasonCode == null || reasonCode.isBlank() ? null : reasonCode,
                safeComment, Instant.now()));
        return repository.find(messageId).orElseThrow();
    }
}
