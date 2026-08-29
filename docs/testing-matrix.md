# Testing Matrix

| Layer | Coverage | Where |
| --- | --- | --- |
| Unit | budget limits, loop detection, prompt registry, reason codes, cost estimation, memory trim/redaction, feedback validation | `BudgetStateTest`, `ExecutionBudgetGuardTest`, `PromptRegistryTest`, `RunRecorderTest`, `RedisChatMemoryTest`, `FeedbackServiceTest` |
| Contract | SSE order/STOP, prompt metadata, run record shape, feedback API validation | `SseEventContractTest`, `GoldenRunReplayTest`, `ChatControllerContractTest` |
| Regression | route dataset (160 cases), golden runs (9 fixtures) | `evaluate_route_agent.py`, `replay_agent_run.py`, `GoldenRunReplayTest` |
| Acceptance | business flow, no-LLM | `scripts/acceptance.sh` |
| Manual | live LLM evaluation (`workflow_dispatch`), load test (`performance/`) | excluded from blocking CI |
| Build gates | JaCoCo core ≥ 90%, Checkstyle, SpotBugs, Ruff, Gitleaks, Helm lint/template, manifest validation | CI |

Deliberately NOT in blocking CI: OWASP dependency DB, Trivy online feed, live-model
comparisons, real provider costs, production metrics.
