package com.tianji.aigc.handoff;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HandoffServiceTest {

    private final HandoffService service = new HandoffService();

    @Test
    void shouldCreateAndLookupRedactedHandoffRequest() {
        HandoffRequest request = service.create(
                "session-9", "request-9", "HUMAN_HANDOFF",
                "退款投诉", "HIGH",
                "我的手机号 13812345678，投诉老师布置的作业太少");

        assertThat(request.handoffId()).startsWith("HF-");
        assertThat(request.status()).isEqualTo(HandoffStatus.REQUESTED);
        assertThat(request.summary()).doesNotContain("13812345678");
        assertThat(request.summary()).contains("[phone redacted]");

        assertThat(service.find(request.handoffId())).contains(request);
    }

    @Test
    void shouldReturnEmptyForUnknownHandoff() {
        assertThat(service.find("HF-unknown")).isEmpty();
    }
}
