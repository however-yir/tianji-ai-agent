# ADR-003: Versioned repository prompts with controlled override

## Context

Business prompts lived in Nacos config only; a prod incident could not be traced to a
specific prompt version and code review did not cover prompt content.

## Decision

Prompt baselines are versioned files in the repository (`prompts/<agent>/v<n>.md`) with
SHA-256 checksums via `PromptRegistry`. Nacos remains a controlled override and every run
record carries prompt id/version/checksum + overridden flag.

## Consequences

Prompts are reviewable and diffable; rollbacks are git operations; run traces answer
"which prompt ran".
