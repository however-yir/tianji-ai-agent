# ADR-001: Deterministic governance over prompt-only safety

## Context

Model instructions alone cannot guarantee safety or termination
(instruction drift, injection, tool loops, cost explosion).

## Decision

Tianji enforces safety with deterministic code — `ActionPolicyGuard`,
`ExecutionBudgetState`, `FlowControl` and `SSE termination contract` — the model only
proposes business actions. Prompt text (versioned) contains behaviour guidance, never
authority.

## Consequences

Safety properties are unit-testable and stable; prompt changes cannot weaken them.
