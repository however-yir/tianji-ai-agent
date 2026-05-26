# Agent Harness

`AgentHarness` 是 `tj-aigc` 内部的业务动作治理层。它不改变 Spring AI Tool Calling 的对外形态，而是在工具方法内部把课程查询、预下单、KnowledgeOps 调用和未来 MCP 调用统一封装为可审计的 `AgentAction`。

## 执行链路

```text
RouteAgent
  -> 子 Agent
  -> CourseTools / OrderTools
  -> AgentAction
  -> ActionPolicyGuard
  -> AgentRuntime
  -> AgentObservation
  -> TRACE / PARAM SSE
```

## 核心类

| 类 | 作用 |
|---|---|
| `AgentAction` | 描述一次业务动作：动作类型、Agent、工具名、requestId、sessionId、userId 和输入参数 |
| `ActionSchema` | 业务动作白名单：`course.query`、`course.search`、`order.preview`、`knowledgeops.rag_query`、`mcp.call` |
| `ActionPolicyDecision` | Policy 结果，表达允许或拒绝以及原因 |
| `ActionPolicyGuard` | 动作治理规则，尤其约束购买链路只允许 `order.preview` |
| `AgentRuntime` | 真正调用业务客户端：课程服务、交易服务、KnowledgeOps |
| `AgentHarnessService` | 串联 policy、runtime 和 observation |
| `AgentObservation` | 一次动作的可观测结果：哪个 Agent 调了哪个工具、是否成功、结果字段、耗时和失败原因 |
| `HarnessEventRecorder` | 将工具结果和 trace 写入 `ToolResultHolder`，供 SSE 输出 |

## 动作 Schema

| Action | 当前状态 | Runtime 行为 |
|---|---|---|
| `course.query` | 已接入 | 调 `CourseClient.baseInfo()`，返回 `CourseInfo`，字段为 `courseInfo_{courseId}` |
| `course.search` | 扩展位 | 先保留 schema，后续接课程搜索 runtime |
| `order.preview` | 已接入 | 调 `TradeClient.prePlaceOrder()`，返回 `PrePlaceOrder`，字段为 `prePlaceOrder` |
| `knowledgeops.rag_query` | 已接入 runtime | 调 `KnowledgeOpsClient.ragSearch()` |
| `mcp.call` | 扩展位 | 先保留 schema，policy 默认拒绝，等待 MCP runtime 接入 |

## 购买链路 Policy

`BuyAgent` 只能触发 `order.preview`。Harness 明确拒绝：

- `order.pay`、`order.confirm` 等非预览订单动作。
- `order.preview` 输入中包含 `pay`、`payment`、`confirm`、`autoConfirm` 等支付或自动确认字段。
- 空课程列表或非正数课程 ID。

因此当前链路只能生成订单确认信息，不能真实支付，也不能自动确认购买。

## SSE 输出

Harness 会把两类数据写入现有事件体系：

- `TRACE(1005)`：输出 `AgentObservation` 核心字段，前端展示“工具执行轨迹”。
- `PARAM(1003)`：继续输出课程卡、订单卡等结构化业务参数，并保留 `agentHarnessTrace` 方便历史回放。

这样前端既能渲染课程/订单卡片，也能看到“哪个 Agent 调了哪个工具、结果如何”。
