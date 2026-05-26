# 企业级生产上线方案

## 范围

本文定义 `tianji-ai-agent` 从展示项目走向企业级生产项目的上线路径。仓库内已具备业务 Agent 主链路、AgentHarness 治理层、SSE 前端、CI、Docker、Helm、Actuator 和 Prometheus 指标；生产落地时还需要补齐真实云资源、密钥、域名、监控平台、合规审批和业务系统联调。

上线主链路：

```text
RouteAgent -> 子Agent -> AgentHarness -> Runtime -> Observation -> SSE
```

## 环境分层

| 环境 | 用途 | 数据 | 发布策略 | 退出条件 |
|---|---|---|---|---|
| local/dev-demo | 本地开发与演示 | Mock 或脱敏样例 | 手动启动 | 前后端核心测试通过 |
| staging | 业务联调、验收、压测 | 脱敏或影子数据 | GitHub Actions 构建镜像，Helm 部署 | 回归、压测、SLO 预检通过 |
| canary | 小流量真实用户验证 | 真实数据 | 单副本或小比例流量 | 关键指标无异常 |
| production | 全量服务 | 真实数据 | 分批升级，可快速回滚 | 发布观察期结束 |

## 发布流水线

1. 开发合并到 `main`。
2. GitHub Actions 运行阻塞主链路：前端 lint/test/build、Python smoke、企业上线校验、Java 核心测试与样例编译。
3. `publish-image.yml` 构建并发布容器镜像。
4. Staging 使用固定镜像 tag 部署。
5. 执行发布清单、冒烟测试、SLO 预检和业务验收。
6. Canary 放量，观察错误率、延迟、SSE 完整率、工具调用失败率和人工转接率。
7. 生产全量发布。
8. 记录发布证据、版本、镜像 digest、负责人和回滚点。

## 发布门禁

| 门禁 | 必须满足 |
|---|---|
| 代码质量 | 阻塞 CI 全绿，advisory 扫描结果已评估 |
| Agent 治理 | `order.preview` 只能预下单，禁止支付和自动确认购买 |
| 配置安全 | 生产密钥只来自 Secret/环境变量，不提交明文 |
| 数据安全 | 日志与 TRACE 不输出完整手机号、身份证、token、API key |
| 可观测性 | `/actuator/health/liveness`、`/actuator/health/readiness`、`/actuator/prometheus` 可访问 |
| 回滚能力 | Helm revision 可回滚，旧镜像 tag 可用 |
| 业务验收 | 课程查询、推荐、预下单、知识问答、转人工路径通过 |

## 生产配置矩阵

| 配置 | 生产要求 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | 使用生产 profile，不使用 `dev-demo` |
| `SERVER_PORT` | 与 Helm Service 保持一致，默认 `8094` |
| MySQL | 独立实例，启用备份、慢查询和连接池告警 |
| Redis | 独立实例，开启持久化、容量告警和高可用 |
| KnowledgeOps | 使用内网域名、服务账号和超时配置 |
| 模型供应商 | 使用专用 key、限额、超时、重试和降级策略 |
| 日志 | JSON 结构化输出，接入集中日志平台 |
| 指标 | Prometheus scrape，核心 SLO 配置告警 |
| 前端 | 静态资源走 CDN 或受控对象存储 |

## 部署命令

Staging 示例：

```bash
helm upgrade --install tianji-ai-agent helm/tianji-ai-agent \
  --namespace tianji-staging \
  --create-namespace \
  --set image.repository=ghcr.io/however-yir/tianji-ai-agent/tj-aigc \
  --set image.tag=<git-sha> \
  --set env.SPRING_PROFILES_ACTIVE=prod
```

生产示例：

```bash
helm upgrade --install tianji-ai-agent helm/tianji-ai-agent \
  --namespace tianji-prod \
  --create-namespace \
  --set image.repository=ghcr.io/however-yir/tianji-ai-agent/tj-aigc \
  --set image.tag=<release-tag> \
  --set env.SPRING_PROFILES_ACTIVE=prod
```

## 冒烟测试

| 场景 | 请求 | 期望 |
|---|---|---|
| 健康检查 | `GET /actuator/health/readiness` | `UP` |
| Prometheus | `GET /actuator/prometheus` | 返回指标文本 |
| 课程推荐 | `POST /chat` 推荐问题 | `ROUTE`、`TRACE`、课程 `PARAM` |
| 课程详情 | `POST /chat` 指定课程 ID | `course.query` observation |
| 预下单 | `POST /chat` 购买问题 | `order.preview` observation，不支付 |
| 知识问答 | `POST /chat` 知识问题 | 流式 `DATA` |
| 转人工 | 支付、投诉或低置信问题 | `HUMAN_HANDOFF` |

## 回滚

1. 暂停放量或切回旧版本流量。
2. 查看 Helm revision：

```bash
helm history tianji-ai-agent -n tianji-prod
```

3. 回滚到上一个稳定版本：

```bash
helm rollback tianji-ai-agent <revision> -n tianji-prod
```

4. 验证 readiness、SSE、课程查询和订单预览。
5. 在发布记录中标注回滚原因、影响面和修复计划。

## 上线完成标准

- `main` 最新 GitHub Actions run 全绿。
- Staging 冒烟测试全部通过。
- Canary 观察期内错误率、延迟和业务指标无异常。
- 运维 Runbook、发布清单、安全治理文档和 300 条路线图已同步。
- 发布负责人、回滚负责人和业务验收负责人明确。
