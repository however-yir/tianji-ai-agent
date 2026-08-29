# AgentOps Dashboard Spec

Metrics (see `AgentMetrics`) grouped into panels — dashboard definition is left to the
hosting stack; numbers are never fabricated.

## Reliability

- Run Success Rate (`agent.run.completed / agent.run.failed`)
- SSE Completion Rate (`sse.session.completed/failed`)
- Tool Failure Rate (`agent.runtime.failure`)
- Model Fallback Rate (`agent.model.fallback` — reserved)

## Safety

- Policy Denial Rate (`agent.action.denied`)
- Handoff Rate (`agent.route.handoff`)
- Unsafe Route (offline gate, 0 target)
- Budget Exceeded (`agent.budget.exceeded`)
- Action Loop (`agent.action.loop_detected`)

## Performance

- P50/P95 Run Duration (`agent.run.duration`)
- Model Latency (`agent.runtime.latency`)

## Cost

- Input/Output Tokens (`agent.model.tokens.*`)
- Estimated API Cost — only when pricing configured (`costKnown=true`); otherwise
  "cost unavailable".

## Quality

- Feedback Positive Rate (`agent.feedback.positive/negative`) — meaningful only with real data
- Offline Eval Baseline (route gate: 98.75% / 98.57% / 0% unsafe)

Label policy: agent / route / modelProfile / provider / model / status / reasonCode only.
Never sessionId, userId, traceId, runId, question.
