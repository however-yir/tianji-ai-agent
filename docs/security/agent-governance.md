# Agent 安全与治理

## 治理原则

- Agent 只能调用注册过的业务动作。
- 业务动作必须先经过 `ActionPolicyGuard`，再进入 `AgentRuntime`。
- 购买链路只允许预下单，不允许真实支付、扣款或自动确认购买。
- 低置信度、投诉、支付敏感和越权风险统一转人工。
- TRACE 与 PARAM 用于解释和审计，但必须避免泄露敏感信息。

## 动作策略表

| 动作 | 状态 | 允许条件 | 禁止事项 |
|---|---|---|---|
| `course.query` | 已启用 | `courseId` 为正数 | 越权读取非公开课程 |
| `course.search` | 扩展位 | `keyword` 非空 | 绕过业务搜索权限 |
| `order.preview` | 已启用 | `courseIds` 为正数集合 | 支付、扣款、确认购买、自动购买 |
| `knowledgeops.rag_query` | 已启用 | `query` 非空，KnowledgeOps 可用 | 泄露未授权知识库 |
| `mcp.call` | 预留 | 后续补 Runtime 与权限模型 | 当前默认执行外部 MCP 工具 |

## 审计字段

每次 Harness 执行至少保留以下字段：

| 字段 | 用途 |
|---|---|
| `sessionId` | 串联一次用户会话 |
| `agentType` | 识别 RouteAgent 或子 Agent |
| `toolName` | 识别 Spring AI Tool 入口 |
| `actionType` | 识别治理动作 |
| `success` | 判断动作成功或失败 |
| `policyAllowed` | 判断是否被策略允许 |
| `policyReason` | 给出允许或拒绝原因 |
| `latencyMs` | 衡量运行时延迟 |
| `resultField` | 映射前端 PARAM 字段 |

## 提示词与工具变更

1. 变更 RouteAgent、BuyAgent 或工具描述时，必须补充或更新路由测试。
2. 任何新增 `order.*` 动作都必须先写 policy 测试，默认拒绝。
3. 任何新增 MCP 工具都必须声明权限、审计字段、超时、重试和降级。
4. 任何可能影响购买、退款、投诉的提示词都需要人工 review。

## 数据保护

- 日志中不得打印完整 token、API key、密码、身份证、手机号和银行卡号。
- 业务 ID 可以用于排障，但生产日志应支持脱敏或 hash。
- 附件内容应限制大小、类型、解析时长和存储时间。
- TRACE 可展示工具调用摘要，不展示密钥、原始鉴权头或内部错误细节。

## 人工兜底

以下情况必须转人工：

- 支付、扣款、退款、确认购买、自动购买。
- 投诉、威胁、法律风险、合规风险。
- RouteAgent 低置信度。
- 课程、订单或 KnowledgeOps 依赖不可用且影响决策。
- 用户明确要求人工客服。

## 验收测试

- `ActionPolicyGuardTest` 覆盖允许与拒绝策略。
- `AgentHarnessServiceTest` 覆盖 policy、runtime 和 observation。
- `CourseToolsHarnessTest` 覆盖课程工具转 Harness。
- `OrderToolsHarnessTest` 覆盖预下单工具转 Harness。
- `AgentServiceImplTest` 覆盖路由到工具后的主链路。
