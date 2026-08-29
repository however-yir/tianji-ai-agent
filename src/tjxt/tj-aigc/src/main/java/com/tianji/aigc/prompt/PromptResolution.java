package com.tianji.aigc.prompt;

/**
 * What actually ran for a promptId: the resolved content plus traceable metadata.
 * {@code overridden} is true when an external config (Nacos) replaced the repository baseline.
 */
public record PromptResolution(String promptId, String version, String content, String checksum, boolean overridden) {
}
