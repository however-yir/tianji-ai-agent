package com.tianji.aigc.prompt;

/**
 * Immutable description of a versioned prompt baseline living in the repository.
 */
public record PromptSpec(String promptId, String version, String content, String description, String checksum) {

    public PromptSpec withContent(String content, String checksumOverride) {
        return new PromptSpec(promptId, version, content, description, checksumOverride);
    }
}
