# 天机 AI 智能体开发实战（代码 + 原型）

这是一个面向 **AI 智能体工程实践** 的学习型仓库，整合了三部分内容：

- `代码/`：后端与智能体实现代码（Spring AI、OpenAI Java、MCP、多模块微服务等）
- `天机agent V0.3/`：Web 交互原型（HTML/CSS/JS）
- `天机AI助手/`：UI 设计稿与页面状态图（JPG/PNG）

仓库目标是把“从大模型接入 -> 业务智能体落地 -> 多智能体工作流 -> MCP 与私有模型”的完整链路串起来，方便学习、演示与二次开发。

## 目录结构

```text
.
├── README.md
├── 代码
│   ├── openai-java-demo
│   ├── my-spring-ai
│   ├── my-spring-ai-mcp
│   └── tjxt
├── 天机agent V0.3
└── 天机AI助手
```

## 功能地图（按课程阶段）

1. 大模型基础与对接
- 主流大模型平台接入（阿里百炼 / OpenAI 兼容接口）
- OpenAI Java SDK 调用示例
- Prompt 工程与安全防护（注入/越狱/数据泄露等）

2. Spring AI 入门到实战
- 普通聊天、流式聊天（SSE）
- System 角色设定、Advisors、会话记忆
- Tool Calling 与业务工具集成
- RAG 检索增强（向量化、检索、问答）

3. 天机 AI 助手业务落地
- 新建会话、历史会话、标题管理
- 自动回复、文本处理、会话记忆
- 课程咨询与购买流程编排

4. 路由工作流与多智能体
- 路由 Agent、推荐 Agent、购买 Agent、咨询 Agent、知识讲解 Agent
- 多智能体任务分发与协作

5. 平台智能体与语音能力
- 在线平台智能体接入
- 通用文本模型能力扩展
- 文本转语音、语音转文本

6. Spring AI 高级能力
- MCP Client / MCP Server
- 多模态输入输出
- 结构化输出（Bean/List/Map/JSON）
- 私有化大模型（Ollama）

## 核心知识点

- Java 17 / Maven 多模块工程
- Spring Boot / Spring Cloud 微服务实践
- Spring AI（ChatClient / Advisor / Tool / RAG）
- OpenAI 兼容接口与流式响应
- Redis 会话记忆、Elasticsearch 知识库
- MCP（Model Context Protocol）客户端与服务端
- 智能体提示词设计、路由与工作流编排
- 语音与多模态 AI 能力集成

## 快速开始

### 1) 环境建议

- JDK 17+
- Maven 3.9+
- MySQL / Redis / Elasticsearch（按子项目需要）

### 2) 关键环境变量

```bash
# 大模型 API Key（阿里百炼/OpenAI兼容）
export ALIYUN_API_KEY="your_key"

# 可选：部分示例会优先读取 AI_API_KEY
export AI_API_KEY="your_key"

# 百炼应用调用测试（tj-aigc 测试代码）
export BAILIAN_USER_TOKEN="your_token"

# MCP 地图服务（按需）
export AMAP_MAPS_API_KEY="your_amap_key"
```

### 3) 示例模块验证

```bash
# OpenAI Java 示例
cd 代码/openai-java-demo
mvn -DskipTests compile

# Spring AI 示例
cd ../my-spring-ai
mvn -DskipTests compile
```

说明：`tjxt` 是完整业务微服务工程，依赖较多；如遇到本地环境或依赖不一致导致的编译问题，请先补齐中间件与配置。

## 原型与 UI 资产

- `天机agent V0.3/`：可直接打开 `start.html`、`index.html` 进行页面流转演示
- `天机AI助手/`：包含聊天窗口、历史会话、语音输入、问答社区等视觉稿

## 安全说明

- 仓库已移除明显硬编码密钥，统一改为环境变量读取。
- 如你继续二次开发，请勿在代码或配置中提交真实 Token/Key。

## 课程文档参考

本 README 的功能与知识点总结参考了以下 7 份课程文档：

1. `1.玩转AI大模型.md`
2. `2.SpringAI入门.md`
3. `3.基本功能.md`
4. `4.课程咨询与购买.md`
5. `5.路由工作流.md`
6. `6.平台智能体、通用文本模型、语音.md`
7. `7.SpringAI高级与私有大模型.md`

---

如果你希望，我可以继续补一版：
- 中文版 + 英文版双语 README
- “一键本地演示”脚本（按子项目自动检测并提示）
