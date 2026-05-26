# 发布检查清单

## 本地提交前

- [ ] `git status --short --branch` 确认在 `main`。
- [ ] `git pull --ff-only` 确认本地 main 最新。
- [ ] `npm ci && npm run lint && npm run test:run && npm run build` 在 `web/chat-ui` 通过。
- [ ] `pytest -q tests` 通过。
- [ ] `bash scripts/validate_enterprise_launch.sh` 通过。
- [ ] `mvn -B -ntp -f src/tjxt/pom.xml -pl tj-aigc -am -DskipTests install` 通过。
- [ ] `mvn -B -ntp -f src/tjxt/tj-aigc/pom.xml test` 通过。
- [ ] `mvn -B -ntp -f src/my-spring-ai/pom.xml -DskipTests package` 通过。
- [ ] `mvn -B -ntp -f src/openai-java-demo/pom.xml -DskipTests package` 通过。
- [ ] `git diff --check` 无空白错误。

## Agent 治理

- [ ] `course.query`、`course.search`、`order.preview`、`knowledgeops.rag_query`、`mcp.call` schema 保持存在。
- [ ] `OrderTools` 只走 `order.preview`，不新增支付、扣款、自动确认动作。
- [ ] `ActionPolicyGuard` 对未知 `order.*` 动作默认拒绝。
- [ ] TRACE/PARAM 能展示 Agent、工具、动作类型、结果和 policy 决策。
- [ ] 低置信度、投诉、支付敏感问题仍转人工。

## 生产配置

- [ ] 生产环境不使用 `dev-demo` profile。
- [ ] 模型 key、KnowledgeOps key、数据库密码和 Redis 密码使用 Secret。
- [ ] CORS、登录鉴权、限流和安全响应头已按生产域名配置。
- [ ] MySQL、Redis、KnowledgeOps、课程服务和交易服务地址为生产内网地址。
- [ ] 日志脱敏策略覆盖 token、手机号、邮箱、身份证和订单号。

## 可观测性

- [ ] `/actuator/health/liveness` 可访问。
- [ ] `/actuator/health/readiness` 可访问。
- [ ] `/actuator/prometheus` 可抓取。
- [ ] Prometheus、日志平台和告警渠道已接入。
- [ ] 核心 SLO、错误率、P95 延迟、工具失败率、SSE 中断率有告警。

## 发布与回滚

- [ ] 使用不可变镜像 tag 或 digest 发布。
- [ ] Helm release history 可查询。
- [ ] 旧版本镜像未清理，可回滚。
- [ ] Staging 冒烟通过后再进入 canary。
- [ ] Canary 观察期指标正常后再全量。
- [ ] 发布记录包含版本、镜像、commit、负责人、时间、风险和回滚点。

## 发布后

- [ ] 查看 GitHub Actions 最新 `main` run 结论为 success。
- [ ] 查看应用错误日志和告警。
- [ ] 抽样检查课程推荐、课程详情、预下单、知识问答和转人工。
- [ ] 更新证据索引和变更说明。
