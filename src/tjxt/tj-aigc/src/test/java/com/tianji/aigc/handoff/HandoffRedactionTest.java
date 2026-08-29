package com.tianji.aigc.handoff;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HandoffRedactionTest {

    @Test
    void shouldMaskPhoneEmailIdAndCardNumber() {
        String raw = "联系 13812345678 或 zhao@example.com，身份证 110101199001011234，卡号 6222021234567890123";

        String redacted = HandoffRedaction.redact(raw);

        assertThat(redacted).doesNotContain("13812345678");
        assertThat(redacted).doesNotContain("zhao@example.com");
        assertThat(redacted).doesNotContain("110101199001011234");
        assertThat(redacted).doesNotContain("6222021234567890123");
        assertThat(redacted).contains("[phone redacted]", "[email redacted]", "[id redacted]", "[card redacted]");
    }

    @Test
    void shouldMaskBearerTokensAndSecretKeys() {
        String raw = "Authorization Bearer sk-abc123def456ghi789 password=Passw0rd1234 token: abcdef12345678";

        String redacted = HandoffRedaction.redact(raw);

        assertThat(redacted).doesNotContain("sk-abc123def456ghi789");
        assertThat(redacted).doesNotContain("Passw0rd1234");
        assertThat(redacted).doesNotContain("abcdef12345678");
    }

    @Test
    void shouldKeepPlainTextIntact() {
        String raw = "课程质量不好，申请售后，谢谢";

        assertThat(HandoffRedaction.redact(raw)).isEqualTo(raw);
    }

    @Test
    void shouldHandleBlankAndNull() {
        assertThat(HandoffRedaction.redact(null)).isNull();
        assertThat(HandoffRedaction.redact("  ")).isEqualTo("  ");
    }
}
