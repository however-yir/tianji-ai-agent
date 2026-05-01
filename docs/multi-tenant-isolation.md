# 多租户隔离设计

## 当前架构

tianji-ai-agent 采用"逻辑隔离 + 租户边界校验"的多租户模式：

| 隔离层 | 实现方式 | 说明 |
|---|---|---|
| 数据库 | 按服务分库（tj_aigc, tj_auth, ...） | 服务级隔离，非租户级 |
| 行级数据 | `user_id` 字段 + 应用层过滤 | 每条数据绑定 `user_id` |
| Redis | 按 `userId` 前缀分区 | 会话、缓存按用户隔离 |
| 聊天记忆 | `RedisChatMemory` 按 `sessionId` | 会话级隔离 |

## 隔离规则

### 1. 聊天会话

- `chat_session.user_id` 作为行级隔离字段
- 查询时必须携带 `user_id` 条件
- Gateway 注入 `X-User-Id` 头，下游服务通过 `UserContext` 获取

### 2. AI Agent 上下文

- `AttachmentContext` 按 `sessionId` 隔离
- `ToolResultHolder` 按请求级 `Map<String, Object>` 隔离
- Agent 的 `GENERATE_STATUS` 按 `sessionId` 管理生成状态

### 3. 聊天记忆（Redis）

- Redis list key 格式：`chat:memory:{sessionId}`
- `sessionId` 含 `userId` 前缀，天然隔离
- TTL 策略：7 天自动过期

## 增强建议

| 维度 | 当前 | 建议 |
|---|---|---|
| 数据层 | `user_id` 行级过滤 | 引入 MyBatis-Plus 租户插件自动注入 |
| 配置层 | 无租户级配置 | 通过 `tenant_id` + 配置表实现差异化 AI 参数 |
| 审计层 | 无统一审计 | 聊天审计日志写入独立表或 ES |
| 资源隔离 | 无 | 按租户限流（令牌桶）+ 配额管理 |

## 关键校验点

```java
// Controller 层：必须从 UserContext 获取 userId
Long userId = UserContext.getUser();

// Service 层：查询必须包含 user_id 条件
LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(ChatSession::getUserId, userId);

// Redis 层：key 必须包含 sessionId（含 userId）
redisTemplate.opsForList().range("chat:memory:" + sessionId, 0, -1);
```
