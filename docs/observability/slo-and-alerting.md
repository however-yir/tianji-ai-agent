# SLO 定义与告警规则

## SLO 定义（tj-aigc AI Agent 服务）

| 指标 | SLO 目标 | 度量方式 | Prometheus 指标 |
|---|---|---|---|
| 可用性 | 99.5%（月） | Actuator health 端点存活率 | `up{job="tj-aigc"}` |
| Agent Runtime P99 | < 3s | Harness action latency | `agent_runtime_latency_seconds` |
| 聊天请求成功率 | > 95% | HTTP 2xx 比例 | `http_server_requests_seconds_count{status=~"2.."} / http_server_requests_seconds_count` |
| Agent action denial rate | 需按业务复核 | Policy Guard 拒绝数 / action 总数 | `agent_action_denied_total / agent_action_total` |
| Agent runtime failure rate | < 5% | Runtime failure / success + failure | `agent_runtime_failure_total` |
| 附件上传成功率 | > 98% | HTTP 2xx + 4xx 客户端错误 | `/attachments` 端点状态码分布 |
| 错误率 | < 2%（5xx） | 服务端错误比例 | `http_server_requests_seconds_count{status=~"5.."}` |

## 告警规则（Prometheus AlertManager）

```yaml
groups:
  - name: tianji-aigc-alerts
    rules:
      # 可用性告警
      - alert: AigcServiceDown
        expr: up{job="tj-aigc"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "tj-aigc 服务不可达"
          description: "{{ $labels.instance }} 已持续 1 分钟无法连接。"

      # 高错误率
      - alert: AigcHighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{job="tj-aigc",status=~"5.."}[5m]))
          / sum(rate(http_server_requests_seconds_count{job="tj-aigc"}[5m])) > 0.02
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "tj-aigc 5xx 错误率超过 2%"
          description: "当前 5xx 错误率: {{ $value | humanizePercentage }}。"

      # Agent Runtime 延迟过高
      - alert: AigcAgentRuntimeHighLatency
        expr: |
          histogram_quantile(0.99,
            sum(rate(agent_runtime_latency_seconds_bucket{job="tj-aigc"}[5m])) by (le)
          ) > 3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "tj-aigc Agent Runtime P99 延迟超过 3s"
          description: "当前 P99 延迟: {{ $value }}s。"

      # Agent runtime failure
      - alert: AigcAgentRuntimeFailureHigh
        expr: |
          sum(rate(agent_runtime_failure_total{job="tj-aigc"}[5m]))
          / clamp_min(sum(rate(agent_runtime_success_total{job="tj-aigc"}[5m])) + sum(rate(agent_runtime_failure_total{job="tj-aigc"}[5m])), 1) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "tj-aigc Agent Runtime failure rate exceeds 5%"
          description: "当前失败率: {{ $value | humanizePercentage }}。"

      # 内存使用过高
      - alert: AigcHighMemory
        expr: jvm_memory_used_bytes{job="tj-aigc",area="heap"} / jvm_memory_max_bytes{job="tj-aigc",area="heap"} > 0.85
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "tj-aigc JVM 堆内存使用率超过 85%"
```

## Prometheus Service Discovery

在 `prometheus.yml` 中添加：

```yaml
scrape_configs:
  - job_name: 'tj-aigc'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['tj-aigc:8094']
```

## 关键端点

| 端点 | 用途 |
|---|---|
| `/actuator/health` | 综合健康检查 |
| `/actuator/health/liveness` | K8s liveness probe |
| `/actuator/health/readiness` | K8s readiness probe |
| `/actuator/prometheus` | Prometheus 指标抓取 |
| `/actuator/info` | 构建信息 |

## Agent Metrics Contract

`AgentMetrics` records only stable, low-cardinality labels such as action name, Agent name and
outcome. `sessionId`、`requestId` 和用户 ID must never become metric labels; they belong in the
sanitized `TRACE(1005)` observation instead.

| Micrometer name | Prometheus name | Meaning |
| --- | --- | --- |
| `agent.route.total` | `agent_route_total` | routing decisions by target Agent |
| `agent.route.handoff` | `agent_route_handoff_total` | deterministic human-hand-off escalation |
| `agent.action.total` | `agent_action_total` | governed action attempts by action/status |
| `agent.action.denied` | `agent_action_denied_total` | policy-denied actions |
| `agent.runtime.success` / `failure` | `agent_runtime_success_total` / `agent_runtime_failure_total` | Runtime outcome |
| `agent.runtime.latency` | `agent_runtime_latency_seconds` | Harness execution timing |
| `sse.session.completed` / `failed` | `sse_session_completed_total` / `sse_session_failed_total` | terminal SSE outcome |
