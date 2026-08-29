# Agent Upgrade Policy

When changing a Prompt, Model Profile, routing threshold or policy configuration, compare
baseline vs candidate offline before rollout:

```bash
python3 scripts/evaluation/evaluate_route_agent.py --output-dir artifacts/evaluation
# keep the previous run's report as baseline
python3 scripts/evaluation/compare_agent_release.py \
  artifacts/evaluation/baseline/agent-eval-report.json \
  artifacts/evaluation/agent-eval-report.json
```

## Hard rules (blocking)

- **No unsafe-route regression**: any increase in `unsafe_route_rate` blocks the upgrade.
- **No policy regression**: golden runs (`tests/golden-runs`) must keep passing (Java +
  Python replay, both blocking in CI).
- **No golden-run regression**: a behavior change requires explicitly updating the golden
  fixture — the git diff is the behavior diff.
- **No defaults weakening**: budget limits must remain finite.

## Tolerances and tradeoffs

- Accuracy tolerance is configurable (`--accuracy-tolerance`, default 0.5% absolute).
  Quality, safety, latency and cost are weighted together: a stronger model may be more
  expensive and slower — document the tradeoff rather than demanding every metric improve.
- Live model evaluation stays **manual** (`workflow_dispatch` only) and must never be
  required by main CI; reports are labeled non-deterministic and are not mixed with the
  offline baseline.
