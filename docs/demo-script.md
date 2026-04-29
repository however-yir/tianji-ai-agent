# Demo Script

这份脚本用于面试、简历讲解、GitHub 项目展示和录屏。目标是把 `tianji-ai-agent` 讲成“课程业务 Agent”，而不是泛泛介绍 Spring AI、MCP 或多模态名词。

## 演示目标

完整展示一条业务链路：

```text
用户输入课程问题 -> RouteAgent 识别意图 -> 子 Agent 调业务工具 -> SSE 流式返回 -> 前端展示课程/订单卡片
```

推荐录屏时长：`60-90 秒`。

## 启动方式

本地演示优先使用 `dev-demo`：

```bash
bash scripts/quick-start-mac.sh
```

访问：

```text
http://127.0.0.1:5173
```

后端：

```text
http://127.0.0.1:8094
```

## 固定演示问题

| 顺序 | 场景 | 用户问题 | 预期路由 | 关键工具 | 前端看点 |
|---|---|---|---|---|---|
| 1 | 课程推荐 | `我零基础，想 3 个月入门 Java 后端，帮我推荐课程` | `RECOMMEND` | `CourseTools.queryCourseById` | 流式推荐说明、课程卡片 |
| 2 | 课程详情查询 | `介绍一下 1589905661084430337 这门课适合谁，价格多少` | `CONSULT` | `CourseTools.queryCourseById` | 课程名称、价格、适用人群 |
| 3 | 预下单 | `我要购买课程 1589905661084430337，帮我生成确认订单` | `BUY` | `OrderTools.prePlaceOrder` | 订单金额、优惠、实付、课程 ID |
| 4 | 知识库问答 | `Java 中 Redis 缓存穿透是什么，怎么处理` | `KNOWLEDGE` | 可选 RAG Advisor | 流式知识回答、历史会话保存 |
| 5 | 语音/多模态入口 | `上传一张课程截图，或用语音问“这门课适合我吗”` | `CONSULT` 或 `KNOWLEDGE` | `/attachment/upload`、`/audio/stt`、`/audio/tts-stream` | 附件引用、语音输入、语音播放入口 |

## 前端和后端对应关系

| 前端动作 | 前端代码 | 后端接口 | 后端职责 |
|---|---|---|---|
| 新建会话 | `createNewSession` | `POST /session?n=4` | 创建 session，返回推荐问题 |
| 加载历史会话 | `refreshApiSessions` | `GET /session/history` | 按时间分组返回历史会话 |
| 切换历史会话 | `loadSessionMessages` | `GET /session/{sessionId}` | 返回用户消息、助手消息和历史参数 |
| 发送问题 | `runApiStreaming` | `POST /chat` | RouteAgent 路由，子 Agent 流式输出 |
| 停止生成 | `handleStopStreaming` | `POST /chat/stop?sessionId=...` | 清理生成状态和附件上下文 |
| 上传附件 | `uploadAttachments` | `POST /attachment/upload` | 解析文本/PDF/DOCX/图片，返回 attachmentId |
| 语音转文本 | 浏览器语音入口或 API 扩展 | `POST /audio/stt` | 把音频文件转成文本问题 |
| 文本转语音 | 语音播放入口 | `POST /audio/tts-stream` | 把助手文本转成音频流 |

## SSE 事件协议

`POST /chat` 返回 `text/event-stream`，前端按 `eventType` 分流：

| eventType | 枚举 | 含义 | 前端处理 |
|---|---|---|---|
| `1001` | `DATA` | 模型文本增量 | 追加到当前助手消息 |
| `1003` | `PARAM` | 工具/附件结构化参数 | 生成课程卡片、订单卡片、引用来源 |
| `1002` | `STOP` | 本轮结束 | 标记消息完成，关闭 loading |

## 讲解词

可以按这段话讲：

> 这个项目我没有把重点放在“接了哪些 AI 框架”，而是把它收敛成课程咨询业务 Agent。用户输入购课问题后，后端先由 RouteAgent 判断意图，再把请求交给推荐、咨询、购买或知识问答子 Agent。涉及课程详情和预下单时，模型不会自己编结果，而是通过 CourseTools 和 OrderTools 调业务微服务。工具结果会以 PARAM 事件和流式文本一起返回，前端就能把普通回答和课程/订单卡片同时展示出来。

## 录屏分镜

1. 打开首页，展示默认聊天窗口和左侧会话列表。
2. 输入第 1 个推荐问题，强调文本是 SSE 逐字输出。
3. 切到第 3 个预下单问题，展示订单卡片和工具参数。
4. 点击停止生成，说明 `/chat/stop` 会清理生成状态。
5. 打开附件或语音入口，说明多模态目前作为入口和扩展位，不抢主线叙事。

## 验收点

- README 首屏能在 30 秒内解释业务闭环。
- `docs/agent-design.md` 能解释 RouteAgent 和子 Agent 的职责边界。
- `web/chat-ui` 能展示 demo 或真实 API 两种模式。
- `tj-aigc` 单元测试覆盖路由、工具、记忆、附件和停止生成关键链路。
- CI 中核心链路失败会阻断，非关键扫描只做 advisory。
