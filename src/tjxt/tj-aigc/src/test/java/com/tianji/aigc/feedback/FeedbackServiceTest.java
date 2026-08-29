package com.tianji.aigc.feedback;

import com.tianji.aigc.run.AgentRunRecord;
import com.tianji.aigc.run.AgentRunRepository;
import com.tianji.aigc.run.InMemoryAgentRunRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackServiceTest {

    private final AgentRunRepository runs = new InMemoryAgentRunRepository(Duration.ofDays(30));
    private final FeedbackRepository feedbacks = new FeedbackRepository();
    private final FeedbackService service = new FeedbackService(feedbacks, runs);

    private String seedRun() {
        String runId = "run-test-1";
        runs.save(AgentRunRecord.builder(runId, runId, "session-fb").build());
        return runId;
    }

    @Test
    void shouldAcceptValidNegativeFeedbackWithRedaction() {
        String runId = seedRun();

        FeedbackRecord record = service.submit(runId, "message-1", "DOWN", "TOOL_ERROR",
                "联系方式 13812345678");

        assertThat(record.feedbackId()).startsWith("fb-");
        assertThat(record.comment()).doesNotContain("13812345678");
        assertThat(record.comment()).contains("[phone redacted]");
        assertThat(feedbacks.size()).isEqualTo(1);
    }

    @Test
    void shouldRejectUnknownRun() {
        assertThatThrownBy(() -> service.submit("run-nope", "m1", "UP", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId 不存在");
    }

    @Test
    void shouldRejectInvalidRatingAndReason() {
        String runId = seedRun();

        assertThatThrownBy(() -> service.submit(runId, "m1", "MAYBE", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rating");
        assertThatThrownBy(() -> service.submit(runId, "m1", "UP", "NOT_A_REASON", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reasonCode");
    }

    @Test
    void shouldCapLongCommentsAndUpsertPerMessage() {
        String runId = seedRun();
        String longComment = "x".repeat(800);

        service.submit(runId, "message-2", "DOWN", null, longComment);

        assertThat(feedbacks.find("message-2").orElseThrow().comment()).hasSize(500);
        // same message updates instead of inserting a second entry
        service.submit(runId, "message-2", "UP", "HELPFUL", "updated");
        assertThat(feedbacks.size()).isEqualTo(1);
        assertThat(feedbacks.find("message-2").orElseThrow().rating()).isEqualTo("UP");
    }
}
