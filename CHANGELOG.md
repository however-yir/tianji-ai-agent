# Changelog

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
