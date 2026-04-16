# Tianji Chat UI

面向 `tj-aigc` 的现代化聊天前端，支持：

- 会话列表与历史消息加载
- `/chat` SSE 流式输出
- `/chat/stop` 停止生成
- 首屏热门示例问题
- 可选 Bearer Token 输入（后端开启鉴权时使用）

## 1. 安装依赖

```bash
npm install
```

## 2. 配置环境变量

复制 `.env.example`（可按需新建 `.env.local`）：

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

默认访问：`http://127.0.0.1:5173`

## 4. 构建产物

```bash
npm run build
```

## 5. 后端启动前提（tj-aigc）

当前后端在本地启动时，至少需要：

- `spring.ai.dashscope.api-key`（或 `spring.ai.dashscope.chat.api-key`）
- MySQL（默认 `127.0.0.1:3306/tj_aigc`）
- Redis（默认 `127.0.0.1:6379`）

若缺少 DashScope Key，`tj-aigc` 会在 Spring 启动阶段失败。
