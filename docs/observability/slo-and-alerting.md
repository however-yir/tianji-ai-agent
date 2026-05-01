# SLO 定义与告警规则

## SLO 定义（tj-aigc AI Agent 服务）

| 指标 | SLO 目标 | 度量方式 | Prometheus 指标 |
|---|---|---|---|
| 可用性 | 99.5%（月） | Actuator health 端点存活率 | `up{job="tj-aigc"}` |
| 首 token 延迟 P99 | < 3s | SSE 首字节时间 | `http_server_requests_seconds{uri="/chat/text",quantile="0.99"}` |
| 聊天请求成功率 | > 95% | HTTP 2xx 比例 | `http_server_requests_seconds_count{status=~"2.."} / http_server_requests_seconds_count` |
| LLM 调用超时率 | < 5% | Spring Retry 失败次数 | 自定义 `ai.llm.timeout.count` |
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

      # 首 token 延迟过高
      - alert: AigcHighLatency
        expr: |
          histogram_quantile(0.99,
            sum(rate(http_server_requests_seconds_bucket{job="tj-aigc",uri="/chat/text"}[5m])) by (le)
          ) > 3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "tj-aigc 聊天接口 P99 延迟超过 3s"
          description: "当前 P99 延迟: {{ $value }}s。"

      # LLM 调用超时
      - alert: AigcLlmTimeoutHigh
        expr: rate(ai_llm_timeout_count_total{job="tj-aigc"}[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "tj-aigc LLM 调用超时频繁"
          description: "每秒超时次数: {{ $value }}。"

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
