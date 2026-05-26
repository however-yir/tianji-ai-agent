# 运维 Runbook

## 目标

本 Runbook 覆盖 `tianji-ai-agent` 生产运行中最常见的故障定位、降级和回滚动作。优先保证用户不被错误引导购买，不执行真实支付，不泄露敏感信息，并能稳定转人工。

## 常用命令

```bash
kubectl get pods -n tianji-prod
kubectl describe pod <pod-name> -n tianji-prod
kubectl logs deploy/tianji-ai-agent-aigc -n tianji-prod --tail=200
kubectl rollout status deploy/tianji-ai-agent-aigc -n tianji-prod
curl -fsS https://<domain>/actuator/health/readiness
curl -fsS https://<domain>/actuator/prometheus
gh run list --branch main --limit 5
gh run view <run-id> --log-failed
```

## 严重等级

| 等级 | 判断标准 | 响应 |
|---|---|---|
| P0 | 错误支付、越权访问、大面积不可用、敏感数据泄露 | 立即回滚或下线入口，通知负责人 |
| P1 | 课程/订单主链路不可用、SSE 大面积失败 | 启用降级和转人工，30 分钟内修复或回滚 |
| P2 | 单一工具失败、少量超时、前端展示异常 | 记录影响面，按发布节奏修复 |
| P3 | 文档、指标、告警噪声 | 排期处理 |

## CI 红灯

1. 查看最新 run：

```bash
gh run list --branch main --limit 10
```

2. 拉失败日志：

```bash
gh run view <run-id> --log-failed
```

3. 在本地复现对应命令。
4. 修复后提交到 `main` 并 `git push origin main`。
5. 继续盯最新 run，直到 `conclusion=success`。

## SSE 无响应或中断

1. 检查前端网络请求是否收到 `DATA`、`TRACE`、`PARAM`、`STOP`。
2. 检查后端日志中的 `sessionId`、目标 Agent 和异常栈。
3. 检查 readiness 与线程池指标。
4. 如果模型供应商超时，临时提高转人工比例或切换降级回答。
5. 如果只影响特定工具，禁用该工具路径并保留普通问答。

## 课程查询失败

1. 检查 `course.query` 的 `AgentObservation` 是否 `success=false`。
2. 检查课程服务 Feign 调用状态码和超时。
3. 检查课程 ID 是否为正数，是否被 policy 拒绝。
4. 课程服务不可用时，前端展示转人工或稍后重试。

## 预下单异常

1. 确认动作类型只能是 `order.preview`。
2. 如果日志出现 `order.pay`、`payment`、`confirmPurchase` 等字段，视为 P0 风险并保留审计。
3. 检查 `ActionPolicyGuard` 是否拒绝了支付或自动确认字段。
4. 交易服务不可用时，不重试真实支付，不生成确认购买状态，直接转人工。

## KnowledgeOps 异常

1. 检查 `TJ_AI_KNOWLEDGEOPS_ENABLED`、base URL、API key。
2. 检查 `knowledgeops.rag_query` observation。
3. RAG 超时时降级为通用回答，并在 TRACE 中保留失败原因。
4. 如果返回质量异常，切换到人工验收知识库或关闭 RAG。

## Redis 异常

1. 检查连接、认证、容量、淘汰策略和慢日志。
2. Redis 不可用时，允许会话短期无记忆降级。
3. 禁止因为记忆缺失自动补全订单确认或支付动作。

## 模型供应商异常

1. 检查错误率、限流、余额、区域故障和超时。
2. 降低 max tokens 或关闭非必要工具。
3. 对购买、投诉、支付敏感问题优先转人工。
4. 恢复后抽样检查 RouteAgent 和 BuyAgent 行为。

## 回滚流程

```bash
helm history tianji-ai-agent -n tianji-prod
helm rollback tianji-ai-agent <revision> -n tianji-prod
kubectl rollout status deploy/tianji-ai-agent-aigc -n tianji-prod
```

回滚后必须验证 readiness、课程查询、预下单、知识问答和转人工。
