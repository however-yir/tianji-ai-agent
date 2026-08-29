# ADR-004: Offline deterministic regression before live model evaluation

## Context

Live LLM evaluation is non-deterministic, needs credentials and is flaky in CI.

## Decision

Blocking CI runs only deterministic gates (offline route contract, golden replay,
manifest validation, Java contract tests). Live evaluation is manual/dispatch-only and
its reports are explicitly labeled non-deterministic, never merged with the offline baseline.

## Consequences

CI stays green without API keys; upgrade decisions combine offline regression (safety)
with manual live checks (quality).
