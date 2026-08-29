# Changelog

## Unreleased

### Added

- `IdempotencyStore` abstraction with in-memory (default) and Redis (`SET NX EX`) production
  implementations; agent-layer duplicate suppression for `order.preview`, fail-closed when the
  store is unavailable.
- Human-handoff contract: `HandoffRequest` with ticket id, status lifecycle (REQUESTED /
  ACKNOWLEDGED / RESOLVED), redacted summary, `ROUTE` event enrichment, front-end
  "已转人工 + handoffId" badge. External contact-center integration is adapter-ready, not included.
- Deterministic fault-injection profile (`fault-injection`) with course/order/RAG timeout,
  HTTP 500, malformed-response and slow-response scenarios, plus
  [docs/failure-matrix.md](docs/failure-matrix.md).
- Hermetic SSE load-test profile (`performance/sse_load_test.py`) and concurrency/race tests
  proving STOP is emitted exactly once and no data follows the terminal event.
- Bounded SSE backpressure buffer (`tj.ai.streaming.buffer-size`, default 256).
- Helm lint + template blocking job, container publish now emits SBOM, provenance and the
  immutably-tagged digest into the workflow summary.
- SSE envelope `version` field (default `1`); dependency baseline decision record
  ([docs/dependency-baseline.md](docs/dependency-baseline.md)).
- **AgentOps Runtime Governance III**: execution budget (model/tool caps, deadline,
  duplicate/loop detection), versioned prompt registry with checksums, run records with
  optional cost estimation, golden-run replay (9 fixtures, blocking), agent release
  manifest (blocking validation), feedback loop (👍/👎 + review candidates), memory
  governance (trim/TTL config/redaction + session deletion), reason codes, ModelProfile
  deterministic selection, `verify.sh`/`Makefile`/`agentops.py` developer entry points,
  ADR set and AgentOps docs.

### Changed

- Spring AI upgraded 1.0.0-M6 -> **1.0.9 GA**; DashScope now uses the OpenAI-compatible
  endpoint via `spring-ai-starter-model-openai` (Spring AI Alibaba starter has no stable
  release). Minor Java API migrations: `RetrievalAugmentationAdvisor`, builder-only
  `MessageChatMemoryAdvisor`, `ChatMemory.get(String)`, `Media` package move, and removal
  of the project-local ChatClient autoconfiguration fork.
- Load test is manual/scheduled only; blocking CI keeps deterministic contract gates.

## v0.1.0-business-agent-showcase - 2026-04-29

这是 `tianji-ai-agent` 从“AI 能力学习合集”重塑为“课程业务 Agent 工程案例”的展示版。

### Highlights

- README 首屏改为课程业务闭环：用户提问 -> RouteAgent -> 子 Agent -> Tool Calling -> SSE -> 前端卡片。
- 新增多智能体路由图、工具调用时序图和 4 张压缩后的聊天界面截图。
- 新增 [docs/demo-script.md](docs/demo-script.md)，固定 5 个演示问题，覆盖推荐、详情、预下单、知识问答、语音/多模态入口。
- 新增 [docs/agent-design.md](docs/agent-design.md)，解释 RouteAgent、子 Agent、工具调用、SSE、记忆、附件和停止生成。
- 新增 [docs/mcp-extension-guide.md](docs/mcp-extension-guide.md)，把 MCP 明确为后续工具生态扩展位。
- 前端 demo 示例改为课程业务问题，并在本地 demo 中返回课程卡片、订单卡片和路由参数。
- `openai-java-demo` 的 TODO 已收口，预下单逻辑明确标记为教学模拟，真实链路指向 `tj-aigc/OrderTools`。
- 新增 `tj-aigc` 单元测试，覆盖 ToolResultHolder、CourseTools、OrderTools、AttachmentContextHolder、Agent 配置和停止生成状态。
- `baseline-ci.yml` 改为核心业务链路阻断，Python 质量建议和密钥扫描保留为 advisory job。

### Release Demo

推荐录屏路径：

```text
打开 Chat UI -> 输入购课问题 -> RouteAgent 命中 BuyAgent -> OrderTools 返回预下单参数 -> SSE 输出 DATA/PARAM/STOP -> 前端展示订单卡片
```

### Verification

- `pytest -q tests`
- `npm run lint`
- `npm run test:run`
- `npm run build`
- `mvn -B -ntp -f src/openai-java-demo/pom.xml -DskipTests package`

`tj-aigc` Maven 验证当前受本机 Maven Central 连接超时影响，详见本次工作总结。
