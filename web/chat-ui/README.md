# Tianji Chat UI

面向 `tj-aigc` 的现代化聊天前端，默认提供“演示模式”，也支持切换到真实后端接口联调。

## 当前能力

- 默认零配置演示模式，避免一打开就遇到 `401`
- 真实 API 模式，接入 `/session`、`/chat`、`/chat/stop`
- 会话搜索、重命名、删除、置顶、导出
- Markdown、GFM、代码高亮、数学公式、Mermaid 图表
- 消息复制、重新生成、浏览器朗读
- 附件入口与语音输入
- 更清晰的错误提示和登录引导

## 1. 安装依赖

```bash
npm install
```

## 2. 配置环境变量

参考 `.env.example`，通常本地只需要：

```bash
VITE_API_BASE_URL=/api
VITE_PROXY_TARGET=http://127.0.0.1:8094
```

说明：

- 浏览器端默认请求 `/api`
- Vite 会把 `/api/**` 代理到 `VITE_PROXY_TARGET`

## 3. 启动前端

```bash
npm run dev
```

默认访问：

```bash
http://127.0.0.1:5173
```

首次打开建议直接用“演示模式”体验，确认界面与交互后，再切到“真实 API”模式。

## 4. 构建产物

```bash
npm run build
```

## 5. 真实 API 模式说明

如果后端开启鉴权，请在左侧填入：

```bash
Bearer <your-token>
```

然后点击“连接真实 API”。

如果后端未开启鉴权，也可以直接匿名尝试连接。

## 6. 附件能力说明

当前前端已经提供附件选择与上下文注入能力：

- 会把文件名、类型、大小加入提问上下文
- 适合演示“基于附件提问”的完整交互

如果你要做真正的文档问答，还需要后端补上传和解析接口。

## 7. 后端启动前提（tj-aigc）

本地启动 `tj-aigc` 时，通常至少需要：

- 模型 API Key（DashScope 或 OpenAI 配置其一）
- MySQL（默认 `127.0.0.1:3306/tj_aigc`）
- Redis（默认 `127.0.0.1:6379`）

如果缺少模型 Key，对应的 Spring Bean 可能会在启动阶段失败。
