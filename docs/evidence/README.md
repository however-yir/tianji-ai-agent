# Tianji AI Agent Evidence Pack

This pack collects the shortest public proof path for reviewing the business Agent workflow.

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
