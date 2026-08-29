# AgentOps & Runtime Governance

Tianji's runtime governance stack: the model proposes business actions; deterministic
**policies**, **execution budgets** and **replayable contracts** govern what actually runs.

```
LLM proposes actions
  -> ActionPolicyGuard decides whether the action is allowed (deny by default, reason codes)
  -> ExecutionBudget enforces model/tool call caps, monotonic deadlines, loop detection
  -> AgentRuntime runs the tool inside the bound
  -> RunRecord persists operational evidence (never reasoning, never credentials)
  -> SSE contract guarantees a STOP
  -> feedback + candidate review + golden replay drive upgrades
```

## Execution Budget

Configured under `tj.ai.execution-budget.*` (see `application.yml`):

| Limit | Default | Meaning |
| --- | ---: | --- |
| max-model-calls | 4 | model invocations per run |
| max-tool-calls | 8 | tool invocations per run |
| max-same-action | 2 | consecutive identical action signatures |
| loop-window | 6 | A-B-A-B detection window |
| max-runtime-seconds | 120 | per-request deadline |
| max-tool-millis | 15000 | cumulative tool time |

Enforcement is runtime-level (`ExecutionBudgetService` + `BudgetState`), never prompt
instructions. Exceeding the budget produces a structured `AgentObservation` with a
`ReasonCode` (`BUDGET_EXCEEDED` / `TOO_MANY_MODEL_CALLS` / `TOO_MANY_TOOL_CALLS` /
`DUPLICATE_ACTION` / `ACTION_LOOP`), terminates gracefully and still emits STOP.

Budgets are per-request and isolated: `BudgetState` lives in the request-scoped holder,
so concurrent sessions never share counters.

## Prompt Governance

Baselines live in `src/main/resources/prompts/<agent>/v<n>.md` - versioned, reviewed,
checksummed (SHA-256). `PromptRegistry` loads them, and Nacos remains a **controlled
override** on top: `PromptResolution` records `promptId`, version, checksum of the
*effective* text and an `overridden` flag. Every run record carries
`promptId/promptVersion/promptChecksum`, so "which prompt caused this?" is answerable
from the trace, not from memory.

Fixed safety segments (Buy state machine, Knowledge untrusted-content boundary) are
appended outside the prompt text and can never be removed by an override.

## Model Runtime

`tj.ai.model-profiles` maps agent roles to deterministic profiles (router / general /
knowledge). The request-level provider/model selection still wins; otherwise the profile
default applies. Token usage is captured from provider metadata when available and cost
estimation uses `tj.ai.model-pricing.*` only when a price is configured - otherwise
`costKnown=false` (never a fabricated 0).

Provider fallback: the runtime currently uses a single OpenAI-compatible provider (see
`docs/dependency-baseline.md`); automatic multi-provider fallback is a tracked follow-up
but is bounded, counted and recorded when implemented.

## Run Records & Replay

`AgentRunRecord` (metadata only):
`runId, traceId, sessionId, route, riskLevel, promptId/version/checksum, modelProfile,
provider, model, timings, modelCallCount, toolCallCount, policyDeniedCount, tokens,
estimatedCost (costKnown flag), terminalStatus, handoff, budgetExceeded, actions[]`.

- Chain-of-thought is never stored or exposed. `ReasonCode`s are stable machine-readable
  decision codes; free text reasons remain human-readable supplements.
- `scripts/replay_agent_run.py` + `GoldenRunReplayTest` replay the deterministic contract
  from `tests/golden-runs/*.json` (route, policy decisions, reason codes, budget/loop
  expectations, terminal STOP). Intentional behavior change = golden fixture change in git.
- Feedback: 👍/👎 on assistant messages -> validated feedback records -> exportable
  candidate cases for **human review**; negative feedback never enters the dataset
  automatically.

## Upgrade Comparison

`scripts/evaluation/compare_agent_release.py baseline.json candidate.json` prints
accuracy/Macro-F1/unsafe-route/handoff (+ cost/latency when available) deltas and enforces
the safety-first policy in `docs/agent-upgrade-policy.md` (unsafe-route regression blocks,
accuracy tolerance advisory-driven).

## Non-promises

- No private chain-of-thought is stored or exposed.
- No automatic prompt/model rollout from feedback.
- No shadow traffic (double-cost) enabled by default.
