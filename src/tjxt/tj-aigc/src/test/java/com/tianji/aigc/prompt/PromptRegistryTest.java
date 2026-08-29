package com.tianji.aigc.prompt;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptRegistryTest {

    private final PromptRegistry registry = PromptRegistryTest.newRegistry();

    private static PromptRegistry newRegistry() {
        PromptRegistry registry = new PromptRegistry();
        registry.init();
        return registry;
    }

    @Test
    void shouldLoadAllRepositoryPromptBaselines() {
        assertThat(registry.size()).isGreaterThanOrEqualTo(9);
        assertThat(registry.active("route")).isPresent();
        assertThat(registry.active("buy")).isPresent();
        assertThat(registry.active("handoff")).isPresent();
        assertThat(registry.active("route").orElseThrow().content()).contains("intent routing");
    }

    @Test
    void shouldComputeStableSha256Checksums() {
        PromptSpec route = registry.active("route").orElseThrow();

        assertThat(route.checksum()).isEqualTo(sha256(route.content()));
        // deterministic across a second registry instance
        assertThat(newRegistry().active("route").orElseThrow().checksum())
                .isEqualTo(route.checksum());
    }

    @Test
    void shouldMarkExternalOverrideAndChecksumTheOverrideContent() {
        String override = "custom routing prompt v2";
        PromptResolution resolution = registry.resolve("route", override);

        assertThat(resolution.overridden()).isTrue();
        assertThat(resolution.content()).isEqualTo(override);
        assertThat(resolution.version()).contains("override");
        assertThat(resolution.checksum()).isEqualTo(sha256(override));
    }

    @Test
    void shouldResolveBaselineWhenNoOverride() {
        PromptResolution resolution = registry.resolve("consult", "");

        assertThat(resolution.overridden()).isFalse();
        assertThat(resolution.checksum()).isEqualTo(registry.active("consult").orElseThrow().checksum());
    }

    @Test
    void shouldRejectUnknownPromptId() {
        assertThatThrownBy(() -> registry.resolve("no-such-prompt", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown promptId");
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
