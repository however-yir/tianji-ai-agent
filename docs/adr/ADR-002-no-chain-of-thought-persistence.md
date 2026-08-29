# ADR-002: No chain-of-thought persistence

## Context

Users and auditors may want to know *why* an agent answered something; storing model
internal reasoning invites leakage, vendor-license and prompt-extraction problems.

## Decision

Tianji records structured operational evidence only: route decision, policy reason codes,
budget decisions, tool outcomes, timings and metrics — never hidden reasoning, never raw
prompt text, never credentials.

## Consequences

Debugging relies on deterministically replayable contracts rather than model introspection.
