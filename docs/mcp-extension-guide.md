# MCP Extension Guide

MCP 在本项目中是扩展位，不是主叙事。课程业务闭环优先由 `tj-aigc` 内部 Agent 和业务工具完成；当工具需要跨项目复用、给其他 Agent 使用或接入外部系统时，再考虑把它抽成 MCP Server。

## 什么时候需要 MCP

适合抽成 MCP：

- 同一个课程查询、订单查询或资料搜索工具要给多个 Agent 项目复用。
- 工具需要独立部署、独立鉴权、独立审计。
- 工具来自外部系统，例如浏览器自动化、地图、企业搜索、工单系统。
- 你希望前端、后端、脚本和 Agent 平台使用统一工具协议。

暂时不需要 MCP：

- 只是 `tj-aigc` 内部调用课程或交易微服务。
- 工具强依赖当前服务的 `UserContext`、事务或内部 Bean。
- 还在验证业务闭环，工具边界没有稳定。

## 当前仓库落点

| 模块 | 作用 |
|---|---|
| `src/my-spring-ai-mcp/my-spring-ai-mcp-server` | MCP Server 示例，暴露工具能力 |
| `src/my-spring-ai-mcp/my-spring-ai-mcp-client` | MCP Client 示例，演示模型通过 MCP 调工具 |
| `src/tjxt/tj-aigc/tools` | 当前课程业务 Agent 的内部工具 |

## 从内部 Tool 迁移到 MCP

建议步骤：

1. 先稳定内部 `CourseTools` 或 `OrderTools` 的入参、出参和错误语义。
2. 抽出纯工具服务，例如 `CourseQueryToolService`，避免直接依赖 Controller。
3. 在 MCP Server 中用 `@Tool` 暴露方法。
4. 在 MCP Client 配置 server 地址。
5. 在 Agent 中替换或并行注册 MCP Tool。
6. 保留原内部 Tool 单元测试，再新增 MCP contract test。

## 工具契约建议

课程查询：

```json
{
  "name": "query_course_by_id",
  "input": {
    "courseId": 1589905661084430337
  },
  "output": {
    "id": 1589905661084430337,
    "name": "Java 后端工程师体系课",
    "price": 199.0,
    "validDuration": 12,
    "usePeople": "零基础或转行学习者",
    "detail": "课程介绍..."
  }
}
```

预下单：

```json
{
  "name": "pre_place_order",
  "input": {
    "courseIds": [1589905661084430337]
  },
  "output": {
    "count": 1,
    "totalAmount": 199.0,
    "discountAmount": 20.0,
    "payAmount": 179.0,
    "courseIds": [1589905661084430337],
    "orderId": 202604290001
  }
}
```

## 安全要求

- 不在 MCP 工具中硬编码 API Key、用户 Token 或课程环境地址。
- 订单类工具必须传递用户身份，并在服务端重新校验权限。
- MCP Server 返回结构化错误，避免模型把异常栈展示给用户。
- 支付、退款、删课等高风险动作不能只靠模型自然语言触发。

## README 讲法

可以这样描述：

> 这个项目主线是课程业务 Agent，MCP 作为后续工具生态扩展点保留。当前课程查询和预下单先以内置 Tool 跑通闭环；当工具契约稳定后，可以把 CourseTools 和 OrderTools 抽成 MCP Server，让其他 Agent 也复用同一套课程和交易能力。
