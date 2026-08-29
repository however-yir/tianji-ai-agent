# RouteAgent Evaluation Checklist

## Offline Blocking Contract

The checked-in JSONL has 160 curated cases across recommendation, consultation,
purchase preview, knowledge, after-sale, study plan, ambiguous requests, complaints
and high-risk requests. `COMPLAINT` remains an intent label; its expected executable
route is `HUMAN_HANDOFF`.

```bash
python3 scripts/evaluation/evaluate_route_agent.py
```

The evaluator writes JSON and Markdown reports under `artifacts/evaluation/` and reports
total cases, accuracy, macro-F1, per-intent precision/recall/F1, fallback/handoff rate,
unsafe-route rate and a confusion matrix. The current deterministic fixture baseline is
**98.75% accuracy**, **98.57% Macro-F1**, and **0% unsafe-route rate**. The blocking
threshold is 98% accuracy and 0% unsafe routing.

This score is deliberately labelled **offline contract**, not live LLM quality. Production
shares `RouteSafetyPolicy` for low-confidence, complaint, payment, privilege-escalation and
prompt-injection handoff behavior; Java tests exercise that component directly.

## Optional Live Evaluation

The same script accepts `--api-url` and an optional bearer token to read `ROUTE(1004)` events
from a running service. This requires a model and is manual/workflow-dispatch only; it is not
part of main CI.

## Required Regression Evidence

- `RouteSafetyPolicyTest`: low confidence, complaint, payment and prompt injection escalation.
- `ActionPolicyGuardTest`: allowlist and direct-payment denial.
- `AgentHarnessServiceTest`: idempotent preview and runtime failure observation.
- `SseEventContractTest` and frontend client tests: payload validation and guaranteed `STOP`.
- `scripts/acceptance.sh`: a no-LLM composed acceptance report.
