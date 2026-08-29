# Tianji AI Agent Evidence Pack

This is the short public proof path for reviewing the business Agent workflow. Every item below
points to runnable code, an executable contract, or a deliberately scoped manual procedure.

## Evidence Index

| What to verify | Evidence |
| --- | --- |
| Product demo | [Demo GIF](../assets/screenshots/demo.gif) and [demo script](../demo-script.md) |
| Core architecture | [README business flow](../../README.md#一眼看懂) and [agent design](../agent-design.md) |
| Agent action governance | [AgentHarness design](../agent-harness.md), [security policy](../security/agent-governance.md), `ActionPolicyGuardTest` |
| Route evaluation | [160-case offline contract](../evaluation/route-agent-dataset.jsonl) and [evaluation checklist](../evaluation/multi-agent-eval-checklist.md) |
| End-to-end acceptance | `bash scripts/acceptance.sh` → `artifacts/acceptance/acceptance-report.json` |
| SSE behavior | [SSE contract](../sse-contract.md), `SseEventContractTest`, `web/chat-ui/src/api/client.test.ts` |
| Buy state machine | [state-machine image](../assets/screenshots/buy-state-machine.svg), `BuyAgent`, `OrderToolsHarnessTest` |
| Observability | [SLO and alerting](../observability/slo-and-alerting.md), `AgentMetrics`, Harness TRACE |
| CI boundary | [CI workflow](../../.github/workflows/ci.yml), [release checklist](../release-checklist.md) |
| Production notes | [launch plan](../production-launch-plan.md), [runbook](../ops/runbook.md) |

Generated evaluation, acceptance and coverage reports are GitHub Actions artifacts; they are not
checked into Git, so the repository does not confuse a historical report with current evidence.

## Runtime Evidence

- Local proof path: `bash scripts/quick-start-mac.sh`
- Pullable image: `docker pull ghcr.io/however-yir/tianji-ai-agent:demo`
- Container workflow: `.github/workflows/publish-image.yml`
- Main CI: `.github/workflows/ci.yml`
- Baseline release: `AI Matrix Baseline 2026.05`
- Release: `v0.1.0 - Course Agent Showcase`
- Frontend app: `web/chat-ui`
- Backend Agent module: `src/tjxt/tj-aigc`

## Product And Architecture Evidence

- Demo GIF: `docs/assets/screenshots/demo.gif`
- Default chat screenshot: `docs/assets/screenshots/chat-default.jpg`
- Course card screenshot: `docs/assets/screenshots/course-card.jpg`
- Order card screenshot: `docs/assets/screenshots/order-card.jpg`
- Agent design: `docs/agent-design.md`
- MCP extension guide: `docs/mcp-extension-guide.md`
- Observability guide: `docs/observability/slo-and-alerting.md`

## Cross-Repo Integration Evidence (KnowledgeOps → tianji)

The following evidence demonstrates the "KnowledgeOps→tianji" matrix link as a runnable code path.

### Prerequisites (3 Environment Variables)

```bash
export TJ_AI_KNOWLEDGEOPS_ENABLED=true
export TJ_AI_KNOWLEDGEOPS_BASE_URL=http://localhost:8080   # KnowledgeOps Agent URL
export TJ_AI_KNOWLEDGEOPS_API_KEY=your-api-key
```

### Verification Steps

1. **KnowledgeOpsClient enrichment**: With `TJ_AI_KNOWLEDGEOPS_ENABLED=true`, send a KNOWLEDGE prompt → tianji's `KnowledgeAgent` calls `KnowledgeOpsClient.ragSearch()` to enrich the response.
2. **RecommendAgent memory enrichment**: With the same config, send a RECOMMEND prompt → `RecommendAgent` calls `KnowledgeOpsClient.memoryQuery()` and `graphSearch()` for richer context.
3. **Fallback without platform**: With `TJ_AI_KNOWLEDGEOPS_ENABLED=false` (default), both agents fall back to local `VectorStore` + `QuestionAnswerAdvisor` without errors or degraded UX.
4. **All 9 Agent types are resolvable**: `AgentServiceImpl` builds the `agentRegistry` from all 9 `Agent` beans (ROUTE, RECOMMEND, CONSULT, BUY, KNOWLEDGE, AFTER_SALE, COMPLAINT, STUDY_PLAN, HUMAN_HANDOFF) — no missing bean errors.
5. **Both CIs are green**: Open the latest GitHub Actions run for both repositories.

### Cross-Repo Docker Compose (Minimal — 2 services + 3 env vars)

```yaml
# docker-compose.cross-repo.yml
version: "3.8"
services:
  knowledgeops:
    image: ghcr.io/however-yir/knowledgeops-agent:latest
    ports: ["8080:8080"]
    environment:
      SPRING_PROFILES_ACTIVE: dev

  tianji-aigc:
    image: ghcr.io/however-yir/tianji-ai-agent:demo
    ports: ["8094:8094"]
    environment:
      TJ_AI_KNOWLEDGEOPS_ENABLED: "true"
      TJ_AI_KNOWLEDGEOPS_BASE_URL: http://knowledgeops:8080
    depends_on: [knowledgeops]
```

### Evidence Artifacts

| Evidence | How to verify |
|---|---|
| KnowledgeAgent uses KnowledgeOps | Send KNOWLEDGE prompt → logs show `KnowledgeOps platform RAG` |
| RecommendAgent uses KnowledgeOps | Send RECOMMEND prompt → logs show `KnowledgeOps platform memory` |
| Fallback works | Set `TJ_AI_KNOWLEDGEOPS_ENABLED=false` → agents use local Advisor |
| All 9 agents register | Check startup logs or `/actuator/beans` for all Agent beans |
| Both CIs green | Latest GitHub Actions run on `main` shows ✓ |

## Verification Checklist

- Start `dev-demo` without external model credentials.
- Send a recommendation prompt and confirm the route event targets `RECOMMEND`.
- Send a course detail prompt and confirm evidence/citation payloads render.
- Send a pre-order prompt and confirm `PARAM` includes the order card data.
- Check memory behavior across multiple turns in the same session.
- Open the latest GitHub Actions run and confirm the CI workflow is green.
- *(Cross-repo)* With KnowledgeOps running, verify `KnowledgeAgent` reaches it via `KnowledgeOpsClient`.
- *(Cross-repo)* With KnowledgeOps stopped, verify `KnowledgeAgent` falls back to local Advisor.

## Interview Q&A Pack

Quick evidence links for the questions interviewers actually ask.

### Q: 为什么这不是普通 Spring AI Demo？

`AgentHarnessService` + `ActionPolicyGuard` + `RouteSafetyPolicy` are a **deterministic**
governance layer sitting between the model and the tools; the model merely proposes
actions. Evidence: [agent-harness.md](../agent-harness.md),
[agent-governance.md](../security/agent-governance.md),
[route evaluation](../evaluation/multi-agent-eval-checklist.md),
`ActionPolicyGuardTest` / `AgentHarnessServiceTest`.

### Q: 模型想直接支付怎么办？

`order.pay` / `payment.execute` / `forcePurchase` are denied by policy before touching any
runtime; `order.preview` is the only allowed buy-side action and requires an authenticated
user. Evidence: `ActionPolicyGuardTest.shouldBlockPaymentAndAutoConfirmationActions`,
buy state machine in [README](../../README.md) 后端核心表.

### Q: 同一个 tool 被调用两次怎么办？

`IdempotencyStore` (Redis `SET NX EX` in production, in-memory for tests) guarantees at
most one executor per `actionType:userId:actionId`; duplicates get the cached result or a
deterministic in-flight hint, and a failed action can retry. Evidence:
`IdempotencyStoreTest`, `RedisIdempotencyStoreTest`,
`AgentHarnessServiceTest.shouldExecuteConcurrentPreviewOnlyOnce`.

### Q: 下游挂了怎么办？

Every failure class is mapped in [failure-matrix.md](../failure-matrix.md) and reproducible
via the `fault-injection` profile (course/order/RAG timeout, HTTP 500, malformed response,
slow response). Runtime failures become FAILED Observations + sanitized TRACE + STOP.

### Q: SSE 会不会卡死？

`SseEventContract` guarantees exactly one STOP and drops events after the terminal one;
the browser treats a stream without STOP as a failure (no infinite loading). The event
buffer is bounded (`tj.ai.streaming.buffer-size`), and the race conditions are covered by
`SseEventContractTest`. Load profile: [performance](../../performance/README.md).

### Q: 你怎么证明升级没有回归？

Blocking gates in CI: offline route evaluator (160 cases, ≥98%, 0 unsafe), Java governance +
SSE tests, JaCoCo core coverage ≥ 90%, acceptance suite, Ruff/Gitleaks/Checkstyle/SpotBugs.
Dependency upgrade decision record: [dependency-baseline.md](../dependency-baseline.md).

### Q: 怎么发布？

`publish-image.yml` builds an immutable `sha-<git-sha>` GHCR tag with OCI labels, SBOM,
provenance and prints the digest into the workflow summary; the Helm chart lints and
renders in blocking CI, and deploys with non-root securityContext.

## AgentOps Q&A Pack (III)

### Q: Prompt 改坏了怎么办？

`PromptRegistry` + versioned `prompts/*/v<n>.md` (SHA-256 checksums); candidate prompt goes
through offline evaluation + golden replay before rollout. Every run records
`promptId/promptVersion/promptChecksum`. Evidence: `PromptRegistryTest`, `GoldenRunReplayTest`,
[agent-upgrade-policy.md](../agent-upgrade-policy.md).

### Q: Agent 无限调用 Tool 怎么办？

`ExecutionBudgetState` caps model/tool calls, deadlines and repeated actions; `ACTION_LOOP`
detects A-B-A-B cycles. Evidence: `ExecutionBudgetGuardTest`, `BudgetStateTest`.

### Q: 这一次「事故」怎么复现？

`AgentRunRecord` (runId + prompt + model + budget + actions) + deterministic replay of the
golden contract (`scripts/replay_agent_run.py`, `GoldenRunReplayTest`). No chain-of-thought
is stored; the replay reproduces decisions, not hidden reasoning.

### Q: 你怎么知道哪个模型更合适？

`tj.ai.model-profiles` + baseline/candidate comparison
(`scripts/evaluation/compare_agent_release.py`) weighting quality / safety / latency / cost.
Live model evaluation remains manual.

### Q: Agent 花多少钱？

Token usage from provider metadata + `tj.ai.model-pricing` table when configured;
otherwise `costKnown=false`. No fabricated 0-cost. Evidence: `RunRecorderTest`.

### Q: 聊天记录会无限增长吗？

Bounded memory: `tj.ai.memory.max-turns` trim, 30-day rolling TTL (configurable), session
deletion clears memory + attachments. Evidence: `RedisChatMemoryTest` + `DELETE /session/{id}`.

### Q: 你怎么用真实用户反馈优化模型？

&#128077;&#128078; on assistant messages -> validated feedback -> candidate cases ->
**human review** -> evaluation dataset. Negative feedback never auto-enters the dataset.

### Q: 你保存模型思维链吗？

**No.** Tianji does not persist or expose private chain-of-thought. It records structured
operational decisions and observable evidence only (see [ADR-002](../adr/ADR-002-no-chain-of-thought-persistence.md)).

## Final Invariant Mapping (RC Audit)

| Invariant | Test |
| --- | --- |
| LLM cannot execute privileged business operations | `ActionPolicyGuardTest` |
| All business actions pass deterministic governance | `AgentHarnessServiceTest`, `ExecutionBudgetGuardTest` |
| BUY never means direct payment | `ActionPolicyGuardTest.shouldBlockPaymentAndAutoConfirmationActions` |
| Dangerous/unknown/low-confidence fail safe | `RouteSafetyPolicyTest`, `AgentServiceImplTest` |
| Every streaming run reaches one deterministic terminal | `SseEventContractTest` (STOP exactly once, late-data suppressed, malformed payload) |
| User A cannot access User B state | session ownership guard (`ChatSessionServiceImpl.assertSessionOwnership`) + key isolation; demo modes documented |
| One request cannot consume unbounded resources | `ExecutionBudgetService`/`BudgetState` caps + bounded SSE buffer |
| Run evidence contains no CoT or secrets | `RunRecorderTest` (metadata only), `HandoffRedactionTest` |
| Same idempotency key cannot duplicate side effects | `IdempotencyStoreTest`, `RedisIdempotencyStoreTest`, harness concurrent-preview test |
| External failures cannot silently cross the safety boundary | `ExecutionBudgetGuardTest.shouldFailClosedWhenIdempotencyStoreIsUnavailable` |

Tianji does not persist or expose private chain-of-thought. It records structured
operational decisions and observable evidence only.
