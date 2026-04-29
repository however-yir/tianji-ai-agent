import {
  Suspense,
  lazy,
  useCallback,
  useDeferredValue,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { ChangeEvent, FormEvent } from "react";
import "./App.css";

const RichMarkdown = lazy(() => import("./components/RichMarkdown"));

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api";
const STORAGE_KEYS = {
  mode: "tianji.chat.run-mode",
  token: "tianji.chat.api-token",
  meta: "tianji.chat.session-meta",
  demo: "tianji.chat.demo-store",
};

const SHORTCUTS = [
  {
    command: "/summary",
    label: "总结会话",
    description: "提炼重点、结论和后续动作",
    prompt: "请总结当前会话的关键信息，输出重点、结论和下一步建议。",
  },
  {
    command: "/todo",
    label: "提取待办",
    description: "自动整理行动清单",
    prompt: "请基于当前内容提取待办事项，按优先级排序，并标记负责人与截止建议。",
  },
  {
    command: "/plan",
    label: "输出方案",
    description: "给出可执行实施方案",
    prompt: "请给出一个可执行的实施方案，包含目标、步骤、风险和验收标准。",
  },
  {
    command: "/review",
    label: "审查建议",
    description: "从风险和缺陷角度看问题",
    prompt: "请以审查者视角分析当前内容，优先指出潜在问题、风险和改进建议。",
  },
  {
    command: "/translate",
    label: "中英互译",
    description: "保留术语与原始语义",
    prompt: "请进行中英互译，保留专业术语、格式结构与原始语义。",
  },
];

const DEMO_EXAMPLES = [
  {
    title: "课程推荐",
    describe: "我零基础，想 3 个月入门 Java 后端，帮我推荐课程",
  },
  {
    title: "课程详情查询",
    describe: "介绍一下 1589905661084430337 这门课适合谁，价格多少",
  },
  {
    title: "预下单",
    describe: "我要购买课程 1589905661084430337，帮我生成确认订单",
  },
  {
    title: "知识库问答",
    describe: "Java 中 Redis 缓存穿透是什么，怎么处理",
  },
  {
    title: "语音/多模态入口",
    describe: "上传一张课程截图，或用语音问“这门课适合我吗”",
  },
];

type RunMode = "demo" | "api";
type MessageRole = "user" | "assistant";
type MessageStatus = "done" | "streaming" | "failed";
type BannerTone = "info" | "warning" | "error";

type AttachmentItem = {
  id: string;
  name: string;
  size: number;
  type: string;
  file?: File;
  uploadId?: string;
  previewText?: string;
  chunkCount?: number;
};

type ReferenceCard = {
  title: string;
  href?: string;
  excerpt?: string;
  tag?: string;
};

type ToolSummaryItem = {
  label: string;
  value: string;
};

type ChatMessage = {
  id: string;
  role: MessageRole;
  content: string;
  createdAt: string;
  status: MessageStatus;
  params?: Record<string, unknown> | null;
  references?: ReferenceCard[];
  toolSummary?: ToolSummaryItem[];
  originQuestion?: string;
  attachments?: AttachmentItem[];
  attachmentIds?: string[];
  errorText?: string;
};

type SessionSummary = {
  id: string;
  title: string;
  updatedAt: string;
  source: RunMode;
  lastSnippet?: string;
};

type DemoSession = {
  id: string;
  title: string;
  updatedAt: string;
  messages: ChatMessage[];
};

type DemoStore = {
  sessions: DemoSession[];
};

type SessionMeta = {
  title?: string;
  pinned?: boolean;
  hidden?: boolean;
};

type BannerState = {
  tone: BannerTone;
  message: string;
};

type ChatEventPayload = {
  eventType?: number;
  eventData?: unknown;
};

type SessionApiResponse = {
  sessionId?: string;
  title?: string;
  describe?: string;
  examples?: Array<{ title?: string; describe?: string }>;
};

type HistoryApiItem = {
  sessionId?: string;
  title?: string;
  updateTime?: string;
};

type MessageApiItem = {
  type?: string;
  content?: string;
  params?: Record<string, unknown>;
};

type AttachmentUploadResponseItem = {
  attachmentId?: string;
  name?: string;
  contentType?: string;
  size?: number;
  previewText?: string;
  chunkCount?: number;
};

type SpeechRecognitionResultLike = {
  transcript: string;
};

type SpeechRecognitionEventLike = {
  results: ArrayLike<ArrayLike<SpeechRecognitionResultLike>>;
};

type SpeechRecognitionErrorLike = {
  error?: string;
};

type SpeechRecognitionInstance = {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onstart: (() => void) | null;
  onend: (() => void) | null;
  onerror: ((event: SpeechRecognitionErrorLike) => void) | null;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  start: () => void;
  stop: () => void;
};

type SpeechRecognitionFactory = new () => SpeechRecognitionInstance;

declare global {
  interface Window {
    SpeechRecognition?: SpeechRecognitionFactory;
    webkitSpeechRecognition?: SpeechRecognitionFactory;
  }
}

class HttpError extends Error {
  status: number;
  payload: unknown;

  constructor(status: number, message: string, payload?: unknown) {
    super(message);
    this.status = status;
    this.payload = payload;
  }
}

function createId(prefix: string): string {
  return `${prefix}-${Math.random().toString(16).slice(2)}-${Date.now().toString(16)}`;
}

function readStorage<T>(key: string, fallback: T): T {
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) {
      return fallback;
    }
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function writeStorage<T>(key: string, value: T) {
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // ignore localStorage quota failures
  }
}

function formatTimestamp(value: string): string {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function formatBytes(size: number): string {
  if (!Number.isFinite(size) || size <= 0) {
    return "0 KB";
  }
  if (size < 1024 * 1024) {
    return `${Math.max(1, Math.round(size / 1024))} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function snapshotAttachments(items: AttachmentItem[]): AttachmentItem[] {
  return items.map(({ id, name, size, type, uploadId, previewText, chunkCount }) => ({
    id,
    name,
    size,
    type,
    uploadId,
    previewText,
    chunkCount,
  }));
}

function stripMarkdown(input: string): string {
  return input
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/!\[[^\]]*]\([^)]+\)/g, " ")
    .replace(/\[[^\]]+]\([^)]+\)/g, "$1")
    .replace(/[#>*_-]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function safeJsonParse(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

function normalizeText(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  if (value == null) {
    return "";
  }
  if (typeof value === "object") {
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return String(value);
    }
  }
  return String(value);
}

function normalizePayload<T>(payload: unknown): T {
  if (
    payload &&
    typeof payload === "object" &&
    "data" in payload &&
    Object.prototype.hasOwnProperty.call(payload, "data")
  ) {
    return (payload as { data: T }).data;
  }
  return payload as T;
}

function createBanner(tone: BannerTone, message: string): BannerState {
  return { tone, message };
}

function errorToMessage(error: unknown): string {
  if (error instanceof HttpError) {
    if (error.status === 401) {
      return "接口返回 401，当前是未登录状态。你可以切换到演示模式，或填写 Bearer Token 后重试。";
    }
    if (error.status === 403) {
      return "接口返回 403，说明当前账号没有访问权限。请检查 Token 对应的权限范围。";
    }
    if (error.status === 404) {
      return "接口不存在或代理目标未启动，请确认后端服务和 `/api` 代理是否正常。";
    }
    if (error.status === 408) {
      return "请求超时了，可能是后端处理较慢或网络不稳定，可以稍后重试。";
    }
    if (error.status === 429) {
      return "请求过于频繁，模型或网关触发限流了，请稍后再试。";
    }
    if (error.status >= 500) {
      return "服务端出现异常，通常是模型服务、数据库或 Redis 没有准备好。";
    }
    return `请求失败：${error.status}`;
  }
  if (error instanceof DOMException && error.name === "AbortError") {
    return "生成已停止。";
  }
  if (error instanceof Error) {
    if (error.message.toLowerCase().includes("timeout")) {
      return "请求超时了，请确认后端和模型接口是否可用。";
    }
    if (error.message) {
      return error.message;
    }
  }
  return "请求失败，请检查网络、代理和后端服务。";
}

function toToolSummary(params?: Record<string, unknown> | null): ToolSummaryItem[] {
  if (!params) {
    return [];
  }
  return Object.entries(params)
    .filter(([key]) => !["sources", "attachmentIds"].includes(key))
    .slice(0, 4)
    .map(([key, value]) => ({
      label: key,
      value: typeof value === "string" ? value : normalizeText(value),
    }));
}

function toReferences(params?: Record<string, unknown> | null): ReferenceCard[] {
  const rawSources = params?.sources;
  if (!Array.isArray(rawSources)) {
    return [];
  }
  return rawSources
    .map((item): ReferenceCard | null => {
      if (!item || typeof item !== "object") {
        return null;
      }
      const source = item as Record<string, unknown>;
      const title = normalizeText(source.attachmentName || source.title);
      if (!title) {
        return null;
      }
      const chunkIndex = source.chunkIndex ? `片段 ${normalizeText(source.chunkIndex)}` : "";
      return {
        title,
        excerpt: normalizeText(source.excerpt),
        tag: chunkIndex || "attachment",
      } satisfies ReferenceCard;
    })
    .filter((item): item is ReferenceCard => item !== null);
}

function extractAttachmentIds(params?: Record<string, unknown> | null): string[] {
  const raw = params?.attachmentIds;
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.map((item) => normalizeText(item)).filter(Boolean);
}

function findShortcut(commandText: string) {
  const trimmed = commandText.trim().toLowerCase();
  return SHORTCUTS.find((item) => trimmed.startsWith(item.command));
}

function resolvePrompt(input: string): string {
  const trimmed = input.trim();
  if (!trimmed.startsWith("/")) {
    return trimmed;
  }
  const [command, ...rest] = trimmed.split(/\s+/);
  const matched = SHORTCUTS.find((item) => item.command === command.toLowerCase());
  if (!matched) {
    return trimmed;
  }
  const tail = rest.join(" ").trim();
  if (!tail) {
    return matched.prompt;
  }
  return `${matched.prompt}\n\n补充说明：${tail}`;
}

function buildAttachmentContext(question: string, attachments: AttachmentItem[]): string {
  if (!attachments.length) {
    return question;
  }
  const attachmentLines = attachments.map((file) => {
    const typeText = file.type || "unknown";
    return `- ${file.name} (${typeText}, ${formatBytes(file.size)})`;
  });
  return `${question}\n\n附件上下文：\n${attachmentLines.join("\n")}\n请结合这些附件信息回答。`;
}

function titleFromQuestion(question: string): string {
  const normalized = stripMarkdown(question).replace(/\s+/g, " ").trim();
  if (!normalized) {
    return "新的对话";
  }
  return normalized.slice(0, 24);
}

function createDemoMessage(
  role: MessageRole,
  content: string,
  extras: Partial<ChatMessage> = {},
): ChatMessage {
  return {
    id: createId(role),
    role,
    content,
    createdAt: new Date().toISOString(),
    status: "done",
    ...extras,
  };
}

function createSeedDemoStore(): DemoStore {
  const sessionAId = createId("demo-session");
  const sessionBId = createId("demo-session");
  return {
    sessions: [
      {
        id: sessionAId,
        title: "课程顾问工作台演示",
        updatedAt: new Date(Date.now() - 1000 * 60 * 38).toISOString(),
        messages: [
          createDemoMessage("user", "帮我总结一下 AI 课程咨询助手应该覆盖哪些关键流程？"),
          createDemoMessage(
            "assistant",
            [
              "可以从 4 个环节组织：",
              "",
              "1. 线索进入：识别用户画像、渠道来源、咨询意图",
              "2. 需求诊断：提问学习目标、基础水平、预算和时间",
              "3. 方案推荐：匹配课程、师资、服务组合和风险提示",
              "4. 转化跟进：记录异议、输出待办、沉淀会话纪要",
              "",
              "```mermaid",
              "flowchart LR",
              'A["线索进入"] --> B["需求诊断"]',
              'B --> C["方案推荐"]',
              'C --> D["转化跟进"]',
              "```",
            ].join("\n"),
            {
              params: {
                mode: "demo",
                workflow: "course-consulting",
                leadScore: 86,
              },
              toolSummary: [
                { label: "workflow", value: "course-consulting" },
                { label: "leadScore", value: "86" },
              ],
              references: [
                {
                  title: "演示说明",
                  excerpt: "这条回复使用本地演示数据，便于零配置体验完整前端。",
                  tag: "demo",
                },
              ],
            },
          ),
        ],
      },
      {
        id: sessionBId,
        title: "前端升级建议",
        updatedAt: new Date(Date.now() - 1000 * 60 * 14).toISOString(),
        messages: [
          createDemoMessage("user", "给我一个把聊天前端升级到更专业水准的路线图。"),
          createDemoMessage(
            "assistant",
            [
              "可以按三个阶段推进：",
              "",
              "- 阶段 1：补默认演示模式、登录闭环、错误提示与历史会话",
              "- 阶段 2：补 Markdown、代码高亮、附件、重试与导出",
              "- 阶段 3：补测试、Docker、本地 mock、性能优化",
              "",
              "如果要估算迭代优先级，可以先做公式与图表展示，例如 $F(x)=\\sum_{i=1}^{n}x_i$ 这类输出会更完整。",
            ].join("\n"),
            {
              params: {
                mode: "demo",
                priority: ["体验", "工程化", "演示能力"],
                eta: "7 days",
              },
              references: [
                {
                  title: "项目建议",
                  excerpt: "优先做用户一进入就能跑通的路径，再叠加高级能力。",
                  tag: "strategy",
                },
              ],
            },
          ),
        ],
      },
    ],
  };
}

function buildDemoReply(question: string, attachments: AttachmentItem[]): Partial<ChatMessage> {
  const normalized = question.toLowerCase();
  const attachmentNames = attachments.map((item) => item.name).join("、");

  if (normalized.includes("推荐课程") || normalized.includes("入门 java") || normalized.includes("java 后端")) {
    return {
      content: [
        "我会先按“目标、基础、周期、转化动作”来推荐课程。",
        "",
        "你是零基础并希望 3 个月入门 Java 后端，优先建议从 Java 基础、Spring Boot 实战、项目就业课三段走。",
        "",
        "推荐顺序：",
        "",
        "1. Java 开发零基础入门：补语法、面向对象和集合基础",
        "2. Spring Boot 企业项目课：把接口、数据库、Redis 串起来",
        "3. Java 后端就业项目课：用完整项目沉淀简历可讲的业务经验",
      ].join("\n"),
      params: {
        mode: "demo",
        route: "RECOMMEND",
        courseInfo_1589905661084430337: {
          id: "1589905661084430337",
          name: "Java 后端工程师体系课",
          price: 199,
          validDuration: 12,
          usePeople: "零基础或转行学习者",
          detail: "覆盖 Java 基础、Spring Boot、Redis、项目实战和面试表达。",
        },
      },
      references: [
        {
          title: "RouteAgent 命中推荐场景",
          excerpt: "推荐类问题会进入 RecommendAgent，再由 CourseTools 返回课程卡片参数。",
          tag: "RECOMMEND",
        },
      ],
    };
  }

  if (normalized.includes("适合谁") || normalized.includes("价格多少") || normalized.includes("课程详情")) {
    return {
      content: [
        "这门课更适合希望系统学习 Java 后端、并需要项目经验沉淀的同学。",
        "",
        "课程会覆盖基础语法、Spring Boot、Redis、接口设计和业务项目实践。当前演示价格为 199 元，有效期 12 个月。",
      ].join("\n"),
      params: {
        mode: "demo",
        route: "CONSULT",
        courseInfo_1589905661084430337: {
          id: "1589905661084430337",
          name: "Java 后端工程师体系课",
          price: 199,
          validDuration: 12,
          usePeople: "零基础、转行学习者、需要补项目经验的后端初学者",
          detail: "以课程详情查询为核心，适合展示 ConsultAgent + CourseTools 链路。",
        },
      },
    };
  }

  if (normalized.includes("购买课程") || normalized.includes("生成确认订单") || normalized.includes("预下单")) {
    return {
      content: [
        "我已经为你生成一张预下单确认卡片。",
        "",
        "在真实链路中，BuyAgent 不会直接完成支付，而是调用 OrderTools 生成订单确认信息，再由前端展示给用户做最终确认。",
      ].join("\n"),
      params: {
        mode: "demo",
        route: "BUY",
        prePlaceOrder: {
          count: 1,
          totalAmount: 199,
          discountAmount: 20,
          couponName: "单券：【新人立减 20 元】",
          payAmount: 179,
          courseIds: ["1589905661084430337"],
          orderId: 202604290001,
          couponId: 9001,
        },
      },
      references: [
        {
          title: "Tool Calling",
          excerpt: "OrderTools.prePlaceOrder 返回结构化订单参数，前端按 PARAM 事件渲染卡片。",
          tag: "BUY",
        },
      ],
    };
  }

  if (normalized.includes("缓存穿透") || normalized.includes("redis")) {
    return {
      content: [
        "Redis 缓存穿透指的是：请求查询一个数据库中也不存在的数据，导致每次都绕过缓存打到数据库。",
        "",
        "常见处理方式：",
        "",
        "1. 缓存空值，并设置较短 TTL",
        "2. 使用布隆过滤器提前拦截不存在的 key",
        "3. 对异常流量做限流和参数校验",
        "",
        "在课程业务里，课程 ID 查询就适合先做参数校验，再结合空值缓存保护课程服务。",
      ].join("\n"),
      params: {
        mode: "demo",
        route: "KNOWLEDGE",
        topic: "Redis 缓存穿透",
      },
    };
  }

  if (normalized.includes("mermaid") || normalized.includes("流程")) {
    return {
      content: [
        "下面给你一个更适合汇报的流程梳理：",
        "",
        "```mermaid",
        "flowchart TD",
        'U["用户提问"] --> R["意图识别"]',
        'R --> P["方案规划"]',
        'P --> T["工具调用 / 参数输出"]',
        'T --> A["流式回答"]',
        "```",
        "",
        "这样做的好处是：结构清晰、可演示、后续也容易接自动化测试。",
      ].join("\n"),
      params: {
        mode: "demo",
        scene: "mermaid",
        attachmentCount: attachments.length,
      },
      references: [
        {
          title: "演示模式说明",
          excerpt: "本地演示会模拟 SSE、参数回传和工具调用展示。",
          tag: "demo",
        },
      ],
    };
  }

  if (normalized.includes("公式") || normalized.includes("math")) {
    return {
      content: [
        "可以，下面给你一个带数学公式的示例：",
        "",
        "当我们用一个简单评分来表示会话质量时，可以写成：",
        "",
        "$$Score = 0.45 \\times Intent + 0.35 \\times Context + 0.20 \\times Action$$",
        "",
        "这样就能把意图识别、上下文完整度和行动建议统一进一个评分体系。",
      ].join("\n"),
      params: {
        mode: "demo",
        scene: "math",
      },
    };
  }

  if (attachments.length) {
    return {
      content: [
        `我已经接收到 ${attachments.length} 个附件的上下文信息：${attachmentNames}。`,
        "",
        "当前前端会把附件名、类型和大小一并加入提问上下文，适合演示“基于附件提问”的交互流程。",
        "",
        "如果你后续想做成真正的文件问答，下一步建议补一个后端上传解析接口，把文档内容或图像 OCR 结果再送入模型。",
      ].join("\n"),
      params: {
        mode: "demo",
        attachmentNames,
        attachmentCount: attachments.length,
      },
      references: [
        {
          title: "附件处理建议",
          excerpt: "当前是前端演示版上下文注入，后续可升级为真实上传与解析。",
          tag: "attachment",
        },
      ],
    };
  }

  if (normalized.includes("测试") || normalized.includes("运行")) {
    return {
      content: [
        "如果目标是“更容易跑起来”，建议优先完成这三件事：",
        "",
        "1. 默认进入 `演示模式`，先保证零配置能体验",
        "2. 提供 `真实 API` 连接面板，明确 Token 和依赖说明",
        "3. 用 `docker-compose` 把 MySQL、Redis、后端、前端串起来",
        "",
        "这样对面试、汇报和团队协作都会友好很多。",
      ].join("\n"),
      params: {
        mode: "demo",
        focus: ["demo", "api", "compose"],
      },
    };
  }

  return {
    content: [
      "这是一个更接近成品的演示回复：",
      "",
      `- 你当前的问题是：${stripMarkdown(question) || "未提供具体问题"}`,
      "- 当前运行模式：本地演示",
      "- 支持会话管理、消息重试、Mermaid、数学公式、附件上下文和语音输入",
      "",
      "如果你切到真实 API 模式，界面会继续走同一套工作流，只是数据改成真实后端返回。",
    ].join("\n"),
      params: {
        mode: "demo",
        confidence: "high",
      },
      references: [
        {
          title: "下一步建议",
          excerpt: "可以继续补 Docker、本地 mock、前端自动化测试和真正的文件上传接口。",
          tag: "next",
        },
      ],
    };
}

function downloadTextFile(fileName: string, content: string) {
  const blob = new Blob([content], { type: "text/markdown;charset=utf-8" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(link.href);
}

async function requestJson<T>(
  path: string,
  init: RequestInit,
  token: string,
  timeoutMs = 15000,
): Promise<T> {
  const controller = new AbortController();
  const headers = new Headers(init.headers);
  let timeoutTriggered = false;
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (token.trim()) {
    headers.set("Authorization", token.trim());
  }
  const timer = window.setTimeout(() => {
    timeoutTriggered = true;
    controller.abort();
  }, timeoutMs);

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
      signal: controller.signal,
    });
    const raw = await response.text();
    const payload = raw ? safeJsonParse(raw) : null;
    if (!response.ok) {
      throw new HttpError(
        timeoutTriggered ? 408 : response.status,
        response.statusText || "Request failed",
        payload,
      );
    }
    return normalizePayload<T>(payload);
  } catch (error) {
    if (timeoutTriggered) {
      throw new Error("timeout");
    }
    throw error;
  } finally {
    window.clearTimeout(timer);
  }
}

async function streamChatEvents(
  payload: { question: string; sessionId: string; attachmentIds?: string[] },
  token: string,
  signal: AbortSignal,
  onEvent: (event: ChatEventPayload) => void,
) {
  const headers = new Headers({
    "Content-Type": "application/json",
  });
  if (token.trim()) {
    headers.set("Authorization", token.trim());
  }
  const response = await fetch(`${API_BASE_URL}/chat`, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
    signal,
  });
  if (!response.ok) {
    const raw = await response.text();
    throw new HttpError(response.status, response.statusText || "Chat request failed", safeJsonParse(raw));
  }
  if (!response.body) {
    throw new Error("后端没有返回可读取的流式响应。");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  const flushBlock = (block: string) => {
    const dataLines = block
      .split(/\r?\n/)
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.replace(/^data:\s?/, ""))
      .join("\n");

    if (!dataLines || dataLines === "[DONE]") {
      return;
    }
    const payload = safeJsonParse(dataLines) as ChatEventPayload;
    onEvent(payload);
  };

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    let separatorIndex = buffer.indexOf("\n\n");
    while (separatorIndex >= 0) {
      const block = buffer.slice(0, separatorIndex).trim();
      buffer = buffer.slice(separatorIndex + 2);
      if (block) {
        flushBlock(block);
      }
      separatorIndex = buffer.indexOf("\n\n");
    }
  }

  const tail = buffer.trim();
  if (tail) {
    flushBlock(tail);
  }
}

async function uploadAttachments(
  attachments: AttachmentItem[],
  token: string,
): Promise<{ uploadedIds: string[]; uploadedItems: AttachmentItem[] }> {
  const formData = new FormData();
  attachments.forEach((attachment) => {
    if (attachment.file) {
      formData.append("files", attachment.file, attachment.name);
    }
  });

  const response = await requestJson<AttachmentUploadResponseItem[]>(
    "/attachment/upload",
    {
      method: "POST",
      body: formData,
    },
    token,
    30000,
  );

  const uploadedItems = (response || []).map((item, index) => ({
    id: attachments[index]?.id || createId("file"),
    name: item.name || attachments[index]?.name || "未命名附件",
    size: item.size || attachments[index]?.size || 0,
    type: item.contentType || attachments[index]?.type || "application/octet-stream",
    uploadId: item.attachmentId || "",
    previewText: item.previewText || "",
    chunkCount: item.chunkCount || 0,
  }));

  return {
    uploadedIds: uploadedItems.map((item) => item.uploadId || "").filter(Boolean),
    uploadedItems,
  };
}

export default function App() {
  const [runMode, setRunMode] = useState<RunMode>(() => readStorage(STORAGE_KEYS.mode, "demo"));
  const [token, setToken] = useState(() => readStorage(STORAGE_KEYS.token, ""));
  const [sessionMeta, setSessionMeta] = useState<Record<string, SessionMeta>>(() =>
    readStorage(STORAGE_KEYS.meta, {}),
  );
  const [demoStore, setDemoStore] = useState<DemoStore>(() =>
    readStorage(STORAGE_KEYS.demo, createSeedDemoStore()),
  );
  const [apiSessions, setApiSessions] = useState<SessionSummary[]>([]);
  const [apiExamples, setApiExamples] = useState<Array<{ title: string; describe: string }>>([]);
  const [messageCache, setMessageCache] = useState<Record<string, ChatMessage[]>>({});
  const [activeSessionId, setActiveSessionId] = useState("");
  const [draft, setDraft] = useState("");
  const [attachments, setAttachments] = useState<AttachmentItem[]>([]);
  const [searchText, setSearchText] = useState("");
  const [sortMode, setSortMode] = useState<"recent" | "pinned">("recent");
  const [banner, setBanner] = useState<BannerState | null>(null);
  const [isSessionLoading, setIsSessionLoading] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);
  const [isConnectingApi, setIsConnectingApi] = useState(false);
  const [apiConnectRequested, setApiConnectRequested] = useState(false);
  const [voiceSupported, setVoiceSupported] = useState(false);
  const [voiceListening, setVoiceListening] = useState(false);
  const [speechError, setSpeechError] = useState("");
  const [speakingMessageId, setSpeakingMessageId] = useState("");

  const abortRef = useRef<AbortController | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const messageListRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const speechRecognitionRef = useRef<SpeechRecognitionInstance | null>(null);
  const currentStreamRef = useRef<{ sessionId: string; messageId: string } | null>(null);
  const deferredSearch = useDeferredValue(searchText);

  useEffect(() => {
    writeStorage(STORAGE_KEYS.mode, runMode);
  }, [runMode]);

  useEffect(() => {
    writeStorage(STORAGE_KEYS.token, token);
  }, [token]);

  useEffect(() => {
    writeStorage(STORAGE_KEYS.meta, sessionMeta);
  }, [sessionMeta]);

  useEffect(() => {
    writeStorage(STORAGE_KEYS.demo, demoStore);
  }, [demoStore]);

  useEffect(() => {
    const factory = window.SpeechRecognition || window.webkitSpeechRecognition;
    setVoiceSupported(Boolean(factory));
  }, []);

  useEffect(() => {
    if (!textareaRef.current) {
      return;
    }
    const element = textareaRef.current;
    element.style.height = "0px";
    element.style.height = `${Math.min(element.scrollHeight, 220)}px`;
  }, [draft]);

  useEffect(() => {
    const list = messageListRef.current;
    if (!list) {
      return;
    }
    list.scrollTop = list.scrollHeight;
  }, [activeSessionId, runMode, isStreaming, demoStore, messageCache]);

  const updateSessionMeta = useCallback((sessionId: string, partial: Partial<SessionMeta>) => {
    setSessionMeta((current) => ({
      ...current,
      [sessionId]: {
        ...current[sessionId],
        ...partial,
      },
    }));
  }, []);

  const demoSessions = useMemo<SessionSummary[]>(() => {
    return demoStore.sessions.map((session) => {
      const lastMessage = session.messages[session.messages.length - 1];
      return {
        id: session.id,
        title: session.title,
        updatedAt: session.updatedAt,
        source: "demo",
        lastSnippet: lastMessage ? stripMarkdown(lastMessage.content).slice(0, 60) : "",
      };
    });
  }, [demoStore.sessions]);

  const baseSessions = runMode === "demo" ? demoSessions : apiSessions;

  const getSessionMessages = useCallback(
    (sessionId: string): ChatMessage[] => {
      if (!sessionId) {
        return [];
      }
      if (runMode === "demo") {
        return demoStore.sessions.find((item) => item.id === sessionId)?.messages || [];
      }
      return messageCache[sessionId] || [];
    },
    [demoStore.sessions, messageCache, runMode],
  );

  const filteredSessions = useMemo(() => {
    const keyword = deferredSearch.trim().toLowerCase();
    const nextSessions = baseSessions
      .filter((session) => !sessionMeta[session.id]?.hidden)
      .filter((session) => {
        if (!keyword) {
          return true;
        }
        const resolvedTitle = sessionMeta[session.id]?.title || session.title || "未命名会话";
        const cachedText = getSessionMessages(session.id)
          .map((message) => stripMarkdown(message.content))
          .join(" ");
        const haystack = [resolvedTitle, session.lastSnippet || "", cachedText]
          .join(" ")
          .toLowerCase();
        return haystack.includes(keyword);
      })
      .sort((left, right) => {
        const leftPinned = sessionMeta[left.id]?.pinned ? 1 : 0;
        const rightPinned = sessionMeta[right.id]?.pinned ? 1 : 0;
        if (leftPinned !== rightPinned) {
          return rightPinned - leftPinned;
        }
        if (sortMode === "pinned") {
          return (sessionMeta[right.id]?.pinned ? 1 : 0) - (sessionMeta[left.id]?.pinned ? 1 : 0);
        }
        return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime();
      });

    return nextSessions;
  }, [baseSessions, deferredSearch, getSessionMessages, sessionMeta, sortMode]);

  const activeMessages = useMemo(() => getSessionMessages(activeSessionId), [activeSessionId, getSessionMessages]);

  const activeSession = useMemo(
    () => filteredSessions.find((session) => session.id === activeSessionId) || baseSessions.find((session) => session.id === activeSessionId),
    [activeSessionId, baseSessions, filteredSessions],
  );

  const activeTitle = activeSession
    ? sessionMeta[activeSession.id]?.title || activeSession.title || "新的对话"
    : "新的对话";

  const slashSuggestions = useMemo(() => {
    const trimmed = draft.trim().toLowerCase();
    if (!trimmed.startsWith("/")) {
      return [];
    }
    return SHORTCUTS.filter((shortcut) => shortcut.command.includes(trimmed));
  }, [draft]);

  const exampleCards = runMode === "demo" ? DEMO_EXAMPLES : apiExamples.length ? apiExamples : DEMO_EXAMPLES;

  const refreshApiSessions = useCallback(async () => {
    setIsConnectingApi(true);
    try {
      const [historyResponse, hotResponse] = await Promise.all([
        requestJson<Record<string, HistoryApiItem[]>>("/session/history", { method: "GET" }, token),
        requestJson<Array<{ title?: string; describe?: string }>>("/session/hot", { method: "GET" }, token),
      ]);

      const nextSessions = Object.values(historyResponse || {})
        .flat()
        .map((item) => ({
          id: item.sessionId || createId("api-session"),
          title: item.title || "未命名会话",
          updatedAt: item.updateTime || new Date().toISOString(),
          source: "api" as const,
          lastSnippet: "",
        }))
        .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime());

      setApiSessions(nextSessions);
      setApiExamples(
        (hotResponse || []).map((item) => ({
          title: item.title || "热门示例",
          describe: item.describe || "",
        })),
      );
      setBanner(null);
      setActiveSessionId((current) => current || nextSessions[0]?.id || "");
    } catch (error) {
      setBanner(createBanner("warning", errorToMessage(error)));
    } finally {
      setIsConnectingApi(false);
    }
  }, [token]);

  const loadSessionMessages = useCallback(
    async (sessionId: string) => {
      if (!sessionId) {
        return;
      }
      if (runMode === "demo") {
        setActiveSessionId(sessionId);
        return;
      }
      setIsSessionLoading(true);
      setActiveSessionId(sessionId);
      try {
        const response = await requestJson<MessageApiItem[]>(`/session/${sessionId}`, { method: "GET" }, token);
        const nextMessages: ChatMessage[] = (response || []).map((item): ChatMessage => ({
          id: createId(item.type === "USER" ? "user" : "assistant"),
          role: item.type === "USER" ? "user" : "assistant",
          content: item.content || "",
          createdAt: new Date().toISOString(),
          status: "done",
          params: item.params || null,
          toolSummary: toToolSummary(item.params),
          references: toReferences(item.params),
          attachmentIds: extractAttachmentIds(item.params),
        }));
        setMessageCache((current) => ({
          ...current,
          [sessionId]: nextMessages,
        }));
        setBanner(null);
      } catch (error) {
        setBanner(createBanner("warning", errorToMessage(error)));
      } finally {
        setIsSessionLoading(false);
      }
    },
    [runMode, token],
  );

  useEffect(() => {
    if (runMode !== "demo") {
      return;
    }
    setApiConnectRequested(false);
    setBanner(null);
    if (!activeSessionId) {
      const firstSession = demoSessions[0];
      if (firstSession) {
        setActiveSessionId(firstSession.id);
      }
    }
  }, [activeSessionId, demoSessions, runMode]);

  useEffect(() => {
    if (runMode === "demo") {
      return;
    }
    if (!apiConnectRequested && !token.trim()) {
      setBanner(createBanner("info", "当前是“真实 API”模式。填写 Bearer Token 后点击连接，或先切回演示模式。"));
      return;
    }

    void refreshApiSessions();
  }, [apiConnectRequested, refreshApiSessions, runMode, token]);

  useEffect(() => {
    if (!activeSessionId) {
      return;
    }
    if (runMode === "api" && !messageCache[activeSessionId] && apiConnectRequested) {
      void loadSessionMessages(activeSessionId);
    }
  }, [activeSessionId, apiConnectRequested, loadSessionMessages, messageCache, runMode]);

  const patchMessagesForSession = useCallback(
    (sessionId: string, updater: (messages: ChatMessage[]) => ChatMessage[]) => {
      if (!sessionId) {
        return;
      }
      if (runMode === "demo") {
        setDemoStore((current) => ({
          sessions: current.sessions.map((session) => {
            if (session.id !== sessionId) {
              return session;
            }
            const nextMessages = updater(session.messages);
            return {
              ...session,
              messages: nextMessages,
              updatedAt: new Date().toISOString(),
            };
          }),
        }));
        return;
      }

      setMessageCache((current) => {
        const nextMessages = updater(current[sessionId] || []);
        return {
          ...current,
          [sessionId]: nextMessages,
        };
      });
      setApiSessions((current) =>
        current.map((session) =>
          session.id === sessionId
            ? {
                ...session,
                updatedAt: new Date().toISOString(),
              }
            : session,
        ),
      );
    },
    [runMode],
  );

  const upsertApiSessionSummary = useCallback((session: SessionSummary) => {
    setApiSessions((current) => {
      const exists = current.some((item) => item.id === session.id);
      if (!exists) {
        return [session, ...current];
      }
      return current.map((item) => (item.id === session.id ? { ...item, ...session } : item));
    });
  }, []);

  const createNewSession = useCallback(async () => {
    setBanner(null);
    if (runMode === "demo") {
      const nextSession: DemoSession = {
        id: createId("demo-session"),
        title: "新的对话",
        updatedAt: new Date().toISOString(),
        messages: [],
      };
      setDemoStore((current) => ({
        sessions: [nextSession, ...current.sessions],
      }));
      setActiveSessionId(nextSession.id);
      return nextSession.id;
    }

    try {
      const response = await requestJson<SessionApiResponse>("/session?n=4", { method: "POST" }, token);
      const sessionId = response.sessionId || createId("api-session");
      upsertApiSessionSummary({
        id: sessionId,
        title: response.title || "新的对话",
        updatedAt: new Date().toISOString(),
        source: "api",
        lastSnippet: "",
      });
      setActiveSessionId(sessionId);
      setMessageCache((current) => ({
        ...current,
        [sessionId]: [],
      }));
      return sessionId;
    } catch (error) {
      setBanner(createBanner("warning", errorToMessage(error)));
      return "";
    }
  }, [runMode, token, upsertApiSessionSummary]);

  const ensureSessionReady = useCallback(async () => {
    if (activeSessionId) {
      return activeSessionId;
    }
    return createNewSession();
  }, [activeSessionId, createNewSession]);

  const handleSessionRename = useCallback(
    (sessionId: string) => {
      const currentTitle =
        sessionMeta[sessionId]?.title ||
        baseSessions.find((session) => session.id === sessionId)?.title ||
        "新的对话";
      const nextTitle = window.prompt("请输入新的会话标题", currentTitle);
      if (!nextTitle) {
        return;
      }
      const normalizedTitle = nextTitle.trim();
      if (!normalizedTitle) {
        return;
      }
      if (runMode === "demo") {
        setDemoStore((current) => ({
          sessions: current.sessions.map((session) =>
            session.id === sessionId ? { ...session, title: normalizedTitle, updatedAt: new Date().toISOString() } : session,
          ),
        }));
      }
      updateSessionMeta(sessionId, { title: normalizedTitle });
    },
    [baseSessions, runMode, sessionMeta, updateSessionMeta],
  );

  const handleSessionDelete = useCallback(
    (sessionId: string) => {
      const confirmed = window.confirm("确认删除这个会话吗？");
      if (!confirmed) {
        return;
      }
      if (runMode === "demo") {
        setDemoStore((current) => ({
          sessions: current.sessions.filter((session) => session.id !== sessionId),
        }));
      } else {
        updateSessionMeta(sessionId, { hidden: true });
      }

      if (activeSessionId === sessionId) {
        const candidate = filteredSessions.find((session) => session.id !== sessionId);
        setActiveSessionId(candidate?.id || "");
      }
    },
    [activeSessionId, filteredSessions, runMode, updateSessionMeta],
  );

  const handleTogglePin = useCallback(
    (sessionId: string) => {
      updateSessionMeta(sessionId, { pinned: !sessionMeta[sessionId]?.pinned });
    },
    [sessionMeta, updateSessionMeta],
  );

  const handleSessionExport = useCallback(
    (sessionId: string) => {
      const session = baseSessions.find((item) => item.id === sessionId);
      const title = sessionMeta[sessionId]?.title || session?.title || "chat-export";
      const messages = getSessionMessages(sessionId);
      const content = [
        `# ${title}`,
        "",
        `- 导出时间：${new Date().toLocaleString("zh-CN")}`,
        `- 模式：${runMode === "demo" ? "演示模式" : "真实 API"}`,
        "",
        ...messages.flatMap((message) => {
          const lines = [
            `## ${message.role === "user" ? "用户" : "助手"} · ${formatTimestamp(message.createdAt)}`,
            "",
            message.content || "(空消息)",
          ];
          if (message.params) {
            lines.push("", "```json", JSON.stringify(message.params, null, 2), "```");
          }
          return [...lines, ""];
        }),
      ].join("\n");
      downloadTextFile(`${title.replace(/\s+/g, "-")}.md`, content);
      setBanner(createBanner("info", "会话已导出为 Markdown 文件。"));
    },
    [baseSessions, getSessionMessages, runMode, sessionMeta],
  );

  const handleClearCurrentSession = useCallback(async () => {
    if (!activeSessionId) {
      return;
    }
    const confirmed = window.confirm("确认清空当前会话吗？");
    if (!confirmed) {
      return;
    }
    if (runMode === "demo") {
      setDemoStore((current) => ({
        sessions: current.sessions.map((session) =>
          session.id === activeSessionId ? { ...session, messages: [], updatedAt: new Date().toISOString() } : session,
        ),
      }));
      setBanner(createBanner("info", "当前演示会话已清空。"));
      return;
    }

    const nextSessionId = await createNewSession();
    if (nextSessionId) {
      setBanner(createBanner("info", "已切换到新的空白会话，原历史仍保留在服务端。"));
    }
  }, [activeSessionId, createNewSession, runMode]);

  const setAssistantState = useCallback(
    (
      sessionId: string,
      messageId: string,
      patch: Partial<ChatMessage> | ((message: ChatMessage) => Partial<ChatMessage>),
    ) => {
      patchMessagesForSession(sessionId, (messages) =>
        messages.map((message) => {
          if (message.id !== messageId) {
            return message;
          }
          const resolvedPatch = typeof patch === "function" ? patch(message) : patch;
          return {
            ...message,
            ...resolvedPatch,
          };
        }),
      );
    },
    [patchMessagesForSession],
  );

  const appendMessages = useCallback(
    (sessionId: string, nextMessages: ChatMessage[]) => {
      patchMessagesForSession(sessionId, (messages) => [...messages, ...nextMessages]);
      const userContent = nextMessages.find((message) => message.role === "user")?.content;
      if (userContent) {
        if (runMode === "demo") {
          setDemoStore((current) => ({
            sessions: current.sessions.map((session) =>
              session.id === sessionId && session.title === "新的对话"
                ? { ...session, title: titleFromQuestion(userContent), updatedAt: new Date().toISOString() }
                : session,
            ),
          }));
        } else {
          setApiSessions((current) =>
            current.map((session) =>
              session.id === sessionId && session.title === "新的对话"
                ? { ...session, title: titleFromQuestion(userContent), updatedAt: new Date().toISOString() }
                : session,
            ),
          );
        }
      }
    },
    [patchMessagesForSession, runMode],
  );

  const handleStopStreaming = useCallback(async () => {
    abortRef.current?.abort();
    const currentStream = currentStreamRef.current;
    if (currentStream) {
      setAssistantState(currentStream.sessionId, currentStream.messageId, (message) => ({
        status: "done",
        content: message.content || "已停止生成。",
      }));
      currentStreamRef.current = null;
    }
    if (runMode === "api" && activeSessionId) {
      try {
        await requestJson<void>(`/chat/stop?sessionId=${encodeURIComponent(activeSessionId)}`, { method: "POST" }, token);
      } catch {
        // stop endpoint failure should not block UI stop feedback
      }
    }
    setIsStreaming(false);
  }, [activeSessionId, runMode, setAssistantState, token]);

  const runDemoStreaming = useCallback(
    async (sessionId: string, messageId: string, question: string, currentAttachments: AttachmentItem[]) => {
      const controller = new AbortController();
      abortRef.current = controller;
      currentStreamRef.current = { sessionId, messageId };
      setIsStreaming(true);

      try {
        const reply = buildDemoReply(question, currentAttachments);
        const chunks = (reply.content || "").match(/.{1,24}/g) || [reply.content || ""];
        for (const chunk of chunks) {
          if (controller.signal.aborted) {
            throw new DOMException("aborted", "AbortError");
          }
          await new Promise((resolve) => window.setTimeout(resolve, 45));
          setAssistantState(sessionId, messageId, (message) => ({
            content: message.content + chunk,
            status: "streaming",
          }));
        }
        setAssistantState(sessionId, messageId, {
          status: "done",
          params: reply.params || null,
          toolSummary: toToolSummary(reply.params || null),
          references: reply.references || [],
        });
        setBanner(null);
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") {
          setBanner(createBanner("info", "已停止当前生成。"));
        } else {
          setAssistantState(sessionId, messageId, {
            status: "failed",
            errorText: errorToMessage(error),
          });
          setBanner(createBanner("warning", errorToMessage(error)));
        }
      } finally {
        setIsStreaming(false);
        abortRef.current = null;
        currentStreamRef.current = null;
      }
    },
    [setAssistantState],
  );

  const runApiStreaming = useCallback(
    async (
      sessionId: string,
      messageId: string,
      question: string,
      attachmentIds: string[] = [],
    ) => {
      const controller = new AbortController();
      abortRef.current = controller;
      currentStreamRef.current = { sessionId, messageId };
      setIsStreaming(true);

      try {
        await streamChatEvents({ question, sessionId, attachmentIds }, token, controller.signal, (event) => {
          if (event.eventType === 1001) {
            setAssistantState(sessionId, messageId, (message) => ({
              content: message.content + normalizeText(event.eventData),
              status: "streaming",
            }));
            return;
          }
          if (event.eventType === 1003) {
            const params =
              event.eventData && typeof event.eventData === "object"
                ? (event.eventData as Record<string, unknown>)
                : { payload: event.eventData };
            setAssistantState(sessionId, messageId, {
              params,
              toolSummary: toToolSummary(params),
              references: toReferences(params),
              attachmentIds: extractAttachmentIds(params),
            });
            return;
          }
          if (event.eventType === 1002) {
            setAssistantState(sessionId, messageId, {
              status: "done",
            });
          }
        });

        setAssistantState(sessionId, messageId, (message) => ({
          status: "done",
          content: message.content || "模型已结束，但没有返回文本内容。",
        }));
        setBanner(null);
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") {
          setBanner(createBanner("info", "已停止当前生成。"));
        } else {
          setAssistantState(sessionId, messageId, {
            status: "failed",
            errorText: errorToMessage(error),
          });
          setBanner(createBanner("warning", errorToMessage(error)));
        }
      } finally {
        setIsStreaming(false);
        abortRef.current = null;
        currentStreamRef.current = null;
        upsertApiSessionSummary({
          id: sessionId,
          title: sessionMeta[sessionId]?.title || activeTitle,
          updatedAt: new Date().toISOString(),
          source: "api",
          lastSnippet: stripMarkdown(getSessionMessages(sessionId).slice(-1)[0]?.content || ""),
        });
      }
    },
    [activeTitle, getSessionMessages, sessionMeta, setAssistantState, token, upsertApiSessionSummary],
  );

  const sendQuestion = useCallback(
    async (
      questionText: string,
      options?: {
        retryMessageId?: string;
        keepUserMessage?: boolean;
        attachmentIds?: string[];
        attachments?: AttachmentItem[];
      },
    ) => {
      const pendingAttachments = options?.attachments || attachments;
      const fallbackPrompt = pendingAttachments.length ? "请基于这些附件内容给我一个总结和建议。" : "";
      const resolvedPrompt = resolvePrompt(questionText || fallbackPrompt);
      if (!resolvedPrompt) {
        return;
      }

      const currentSessionId = await ensureSessionReady();
      if (!currentSessionId) {
        return;
      }

      let attachmentIds = options?.attachmentIds || [];
      let messageAttachments = options?.attachments || snapshotAttachments(pendingAttachments);
      const attachmentsNeedingUpload = pendingAttachments.filter((item) => item.file);

      if (runMode === "api" && attachmentsNeedingUpload.length) {
        const uploadResult = await uploadAttachments(attachmentsNeedingUpload, token);
        attachmentIds = uploadResult.uploadedIds;
        messageAttachments = uploadResult.uploadedItems;
        setBanner(createBanner("info", "附件已上传，正在基于解析内容检索相关片段。"));
      }

      const questionWithAttachments = buildAttachmentContext(resolvedPrompt, pendingAttachments);
      const assistantMessageId = options?.retryMessageId || createId("assistant");
      const userMessage = createDemoMessage("user", resolvedPrompt, {
        attachments: messageAttachments,
        attachmentIds,
      });

      if (options?.retryMessageId) {
        setAssistantState(currentSessionId, options.retryMessageId, {
          content: "",
          status: "streaming",
          params: null,
          toolSummary: [],
          references: [],
          errorText: "",
          originQuestion: resolvedPrompt,
          attachmentIds,
          attachments: messageAttachments,
        });
      } else {
        const assistantMessage: ChatMessage = {
          id: assistantMessageId,
          role: "assistant",
          content: "",
          createdAt: new Date().toISOString(),
          status: "streaming",
          originQuestion: resolvedPrompt,
          attachments: messageAttachments,
          attachmentIds,
        };
        appendMessages(
          currentSessionId,
          options?.keepUserMessage ? [assistantMessage] : [userMessage, assistantMessage],
        );
      }

      setDraft("");
      setAttachments([]);
      setSpeechError("");
      setBanner(null);

      if (runMode === "demo") {
        await runDemoStreaming(currentSessionId, assistantMessageId, questionWithAttachments, pendingAttachments);
        return;
      }

      await runApiStreaming(currentSessionId, assistantMessageId, resolvedPrompt, attachmentIds);
    },
    [appendMessages, attachments, ensureSessionReady, runApiStreaming, runMode, runDemoStreaming, setAssistantState, token],
  );

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      if ((!draft.trim() && !attachments.length) || isStreaming) {
        return;
      }
      await sendQuestion(draft);
    },
    [attachments.length, draft, isStreaming, sendQuestion],
  );

  const handleRetry = useCallback(
    async (message: ChatMessage) => {
      const prompt = message.originQuestion;
      if (!prompt || isStreaming) {
        return;
      }
      await sendQuestion(prompt, {
        retryMessageId: message.id,
        keepUserMessage: true,
        attachmentIds: message.attachmentIds,
        attachments: message.attachments,
      });
    },
    [isStreaming, sendQuestion],
  );

  const handleCopyMessage = useCallback(async (content: string) => {
    try {
      await navigator.clipboard.writeText(content);
      setBanner(createBanner("info", "消息内容已复制。"));
    } catch {
      setBanner(createBanner("warning", "复制失败，请检查浏览器剪贴板权限。"));
    }
  }, []);

  const toggleSpeechPlayback = useCallback((message: ChatMessage) => {
    if (!("speechSynthesis" in window)) {
      setBanner(createBanner("warning", "当前浏览器不支持文本朗读。"));
      return;
    }
    if (speakingMessageId === message.id) {
      window.speechSynthesis.cancel();
      setSpeakingMessageId("");
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(stripMarkdown(message.content));
    utterance.lang = "zh-CN";
    utterance.onend = () => {
      setSpeakingMessageId("");
    };
    utterance.onerror = () => {
      setSpeakingMessageId("");
    };
    setSpeakingMessageId(message.id);
    window.speechSynthesis.speak(utterance);
  }, [speakingMessageId]);

  const handleAttachmentPick = useCallback((event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || []);
    const nextFiles = files.map((file) => ({
      id: createId("file"),
      name: file.name,
      size: file.size,
      type: file.type || "application/octet-stream",
      file,
    }));
    setAttachments((current) => [...current, ...nextFiles]);
    event.target.value = "";
  }, []);

  const removeAttachment = useCallback((attachmentId: string) => {
    setAttachments((current) => current.filter((item) => item.id !== attachmentId));
  }, []);

  const handleStartVoice = useCallback(() => {
    const factory = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!factory) {
      setSpeechError("当前浏览器不支持语音输入。");
      return;
    }
    if (!speechRecognitionRef.current) {
      const recognition = new factory();
      recognition.continuous = false;
      recognition.interimResults = false;
      recognition.lang = "zh-CN";
      recognition.onstart = () => {
        setVoiceListening(true);
        setSpeechError("");
      };
      recognition.onend = () => {
        setVoiceListening(false);
      };
      recognition.onerror = (event) => {
        setVoiceListening(false);
        setSpeechError(event.error || "语音识别失败。");
      };
      recognition.onresult = (event) => {
        const transcript = Array.from(event.results)
          .flatMap((result) => Array.from(result))
          .map((item) => item.transcript)
          .join("");
        if (transcript) {
          setDraft((current) => [current, transcript].filter(Boolean).join(current ? "\n" : ""));
        }
      };
      speechRecognitionRef.current = recognition;
    }
    if (voiceListening) {
      speechRecognitionRef.current.stop();
      return;
    }
    speechRecognitionRef.current.start();
  }, [voiceListening]);

  const handleModeSwitch = useCallback((mode: RunMode) => {
    setRunMode(mode);
    setBanner(null);
    if (mode === "demo") {
      if (!activeSessionId && demoSessions[0]) {
        setActiveSessionId(demoSessions[0].id);
      }
      return;
    }
    setApiConnectRequested(false);
  }, [activeSessionId, demoSessions]);

  const connectApi = useCallback(() => {
    setApiConnectRequested(true);
    setBanner(createBanner("info", "正在尝试连接真实 API..."));
  }, []);

  const activeSessionEmpty = activeMessages.length === 0;
  const showAuthCard = runMode === "api" && !apiConnectRequested && !token.trim();
  const currentShortcut = findShortcut(draft);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <p className="brand-kicker">TIANJI AGENT</p>
          <h1>对话工作台</h1>
          <p className="brand-desc">
            默认零配置演示，切到真实 API 时再接入 Token、后端和模型服务。
          </p>
        </div>

        <section className="sidebar-group">
          <div className="group-title-row">
            <h2>运行模式</h2>
            <span>{runMode === "demo" ? "零配置" : "联调"}</span>
          </div>
          <div className="mode-switch">
            <button
              className={runMode === "demo" ? "mode-btn active" : "mode-btn"}
              onClick={() => handleModeSwitch("demo")}
              type="button"
            >
              演示模式
            </button>
            <button
              className={runMode === "api" ? "mode-btn active" : "mode-btn"}
              onClick={() => handleModeSwitch("api")}
              type="button"
            >
              真实 API
            </button>
          </div>
          <p className="mode-tip">
            {runMode === "demo"
              ? "本地模拟 SSE、参数输出、Mermaid、公式和会话管理，适合先看效果。"
              : "切换到真实 API 后，会走 `/session`、`/chat` 和 `/chat/stop` 接口。"}
          </p>
        </section>

        <button className="new-chat-btn" onClick={() => void createNewSession()} type="button">
          + 新建对话
        </button>

        <section className="sidebar-group">
          <div className="group-title-row">
            <h2>历史会话</h2>
            <span>{filteredSessions.length}</span>
          </div>
          <div className="history-toolbar">
            <input
              className="history-search"
              value={searchText}
              onChange={(event) => setSearchText(event.target.value)}
              placeholder="搜索标题或消息内容"
            />
            <button
              className="clear-search-btn"
              onClick={() => setSearchText("")}
              type="button"
            >
              清空
            </button>
          </div>
          <div className="history-sort-row">
            <button
              className={sortMode === "recent" ? "mini-tab active" : "mini-tab"}
              onClick={() => setSortMode("recent")}
              type="button"
            >
              最近更新
            </button>
            <button
              className={sortMode === "pinned" ? "mini-tab active" : "mini-tab"}
              onClick={() => setSortMode("pinned")}
              type="button"
            >
              置顶优先
            </button>
          </div>
          <div className="history-list">
            {filteredSessions.length ? (
              filteredSessions.map((session) => {
                const resolvedTitle = sessionMeta[session.id]?.title || session.title;
                const sessionMessages = getSessionMessages(session.id);
                const lastSnippet =
                  session.lastSnippet || stripMarkdown(sessionMessages[sessionMessages.length - 1]?.content || "");
                const pinned = sessionMeta[session.id]?.pinned;
                return (
                  <div
                    className={activeSessionId === session.id ? "history-item active" : "history-item"}
                    key={session.id}
                  >
                    <button
                      className="history-main"
                      onClick={() => void loadSessionMessages(session.id)}
                      type="button"
                    >
                      <div className="history-title-row">
                        <span className="history-title">{resolvedTitle}</span>
                        {pinned ? <span className="history-pin">置顶</span> : null}
                      </div>
                      <span className="history-time">{formatTimestamp(session.updatedAt)}</span>
                      <small className="history-snippet">{lastSnippet || "暂无消息内容"}</small>
                    </button>
                    <div className="history-actions">
                      <button onClick={() => handleTogglePin(session.id)} type="button">
                        {pinned ? "取消置顶" : "置顶"}
                      </button>
                      <button onClick={() => handleSessionRename(session.id)} type="button">
                        重命名
                      </button>
                      <button onClick={() => handleSessionExport(session.id)} type="button">
                        导出
                      </button>
                      <button onClick={() => handleSessionDelete(session.id)} type="button">
                        删除
                      </button>
                    </div>
                  </div>
                );
              })
            ) : (
              <p className="placeholder">暂无匹配会话</p>
            )}
          </div>
        </section>

        <section className="sidebar-group token-panel">
          <div className="group-title-row">
            <h2>API 连接</h2>
            <span>{runMode === "demo" ? "可选" : "建议填写"}</span>
          </div>
          <input
            value={token}
            onChange={(event) => setToken(event.target.value)}
            placeholder="Bearer Token（可选）"
          />
          <p className="token-tip">
            如果后端开启鉴权，请填写 `Bearer xxx`；否则可直接匿名尝试连接。
          </p>
          <button className="token-action-btn" onClick={connectApi} type="button">
            {isConnectingApi ? "连接中..." : "连接真实 API"}
          </button>
        </section>
      </aside>

      <main className="chat-main">
        <header className="chat-header">
          <div>
            <p className="header-label">当前会话</p>
            <h2>{activeTitle}</h2>
          </div>
          <div className="header-actions">
            <span className="status-pill">{runMode === "demo" ? "演示模式" : "真实 API"}</span>
            <button className="ghost-btn" onClick={() => handleSessionExport(activeSessionId)} type="button" disabled={!activeSessionId}>
              导出当前会话
            </button>
            <button className="ghost-btn" onClick={() => void handleClearCurrentSession()} type="button" disabled={!activeSessionId}>
              清空当前会话
            </button>
            {isStreaming ? (
              <button className="stop-btn" onClick={() => void handleStopStreaming()} type="button">
                停止生成
              </button>
            ) : null}
          </div>
        </header>

        <section className="message-panel">
          {showAuthCard ? (
            <div className="auth-card">
              <span className="auth-badge">连接向导</span>
              <h3>先连接真实 API，再查看后端数据</h3>
              <p>
                当前默认不主动请求接口，所以不会一进入就看到 401。你可以直接粘贴 Token，
                或者先切回演示模式继续体验完整流程。
              </p>
              <div className="auth-actions">
                <button className="primary-btn" onClick={connectApi} type="button">
                  使用当前配置连接
                </button>
                <button className="ghost-btn" onClick={() => handleModeSwitch("demo")} type="button">
                  先用演示模式
                </button>
              </div>
            </div>
          ) : null}

          {activeSessionEmpty && !showAuthCard ? (
            <div className="welcome-panel">
              <h3>开始一段高质量对话</h3>
              <p>
                支持历史会话、附件上下文、Markdown 渲染、公式、Mermaid、消息重试与语音输入。
              </p>
              <div className="example-grid">
                {exampleCards.map((item) => (
                  <button
                    className="example-card"
                    key={item.title}
                    onClick={() => setDraft(item.title)}
                    type="button"
                  >
                    <span>{item.title}</span>
                    <small>{item.describe}</small>
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {isSessionLoading ? <div className="loading-tip">正在加载会话内容...</div> : null}

          <div className="message-list" ref={messageListRef}>
            {activeMessages.map((message) => (
              <article className={message.role === "user" ? "message-row user" : "message-row"} key={message.id}>
                <div className="avatar">{message.role === "user" ? "我" : "AI"}</div>
                <div className="bubble-wrap">
                  <div
                    className={[
                      "bubble",
                      message.status === "streaming" ? "pending" : "",
                      message.status === "failed" ? "failed" : "",
                    ]
                      .filter(Boolean)
                      .join(" ")}
                  >
                    {message.role === "assistant" ? (
                      <Suspense fallback={<div className="plain-text">{message.content || "生成中..."}</div>}>
                        <RichMarkdown content={message.content || "生成中..."} />
                      </Suspense>
                    ) : (
                      <div className="plain-text">{message.content}</div>
                    )}
                  </div>

                  {message.attachments?.length ? (
                    <div className="attachment-list">
                      {message.attachments.map((item) => (
                        <span className="attachment-chip static" key={item.id}>
                          {item.name} · {formatBytes(item.size)}
                        </span>
                      ))}
                    </div>
                  ) : null}

                  {message.toolSummary?.length ? (
                    <details className="fold-card">
                      <summary>工具与参数摘要</summary>
                      <div className="summary-grid">
                        {message.toolSummary.map((item) => (
                          <div className="summary-item" key={`${message.id}-${item.label}`}>
                            <strong>{item.label}</strong>
                            <span>{item.value}</span>
                          </div>
                        ))}
                      </div>
                    </details>
                  ) : null}

                  {message.params ? (
                    <details className="fold-card">
                      <summary>完整参数</summary>
                      <pre className="params-block">{JSON.stringify(message.params, null, 2)}</pre>
                    </details>
                  ) : null}

                  {message.references?.length ? (
                    <details className="fold-card">
                      <summary>引用与说明</summary>
                      <div className="reference-list">
                        {message.references.map((reference) => (
                          <article className="reference-card" key={`${message.id}-${reference.title}`}>
                            <div className="reference-head">
                              <strong>{reference.title}</strong>
                              {reference.tag ? <span>{reference.tag}</span> : null}
                            </div>
                            {reference.excerpt ? <p>{reference.excerpt}</p> : null}
                            {reference.href ? (
                              <a href={reference.href} rel="noreferrer" target="_blank">
                                打开链接
                              </a>
                            ) : null}
                          </article>
                        ))}
                      </div>
                    </details>
                  ) : null}

                  <div className="message-actions">
                    <button onClick={() => void handleCopyMessage(message.content)} type="button">
                      复制
                    </button>
                    {message.role === "assistant" ? (
                      <button onClick={() => toggleSpeechPlayback(message)} type="button">
                        {speakingMessageId === message.id ? "停止朗读" : "朗读"}
                      </button>
                    ) : null}
                    {message.role === "assistant" ? (
                      <button
                        disabled={isStreaming || !message.originQuestion}
                        onClick={() => void handleRetry(message)}
                        type="button"
                      >
                        {message.status === "failed" ? "重试" : "重新生成"}
                      </button>
                    ) : null}
                  </div>
                </div>
              </article>
            ))}
          </div>
        </section>

        {banner ? (
          <div className={`banner banner-${banner.tone}`}>
            {banner.message}
          </div>
        ) : null}

        <form className="composer" onSubmit={handleSubmit}>
          <div className="shortcut-panel">
            {SHORTCUTS.map((shortcut) => (
              <button
                className={currentShortcut?.command === shortcut.command ? "shortcut-btn active" : "shortcut-btn"}
                key={shortcut.command}
                onClick={() => setDraft(shortcut.prompt)}
                type="button"
              >
                {shortcut.label}
              </button>
            ))}
          </div>

          <div className="composer-toolbar">
            <button className="toolbar-btn" onClick={() => fileInputRef.current?.click()} type="button">
              添加附件
            </button>
            <button
              className={voiceListening ? "toolbar-btn active" : "toolbar-btn"}
              disabled={!voiceSupported}
              onClick={handleStartVoice}
              type="button"
            >
              {voiceSupported ? (voiceListening ? "停止收音" : "语音输入") : "浏览器不支持语音"}
            </button>
            <span className="composer-hint">
              Enter 发送，Shift+Enter 换行，输入 `/` 可快速调出提示指令
            </span>
          </div>

          <input
            hidden
            multiple
            onChange={handleAttachmentPick}
            ref={fileInputRef}
            type="file"
          />

          {attachments.length ? (
            <div className="attachment-list">
              {attachments.map((item) => (
                <span className="attachment-chip" key={item.id}>
                  {item.name} · {formatBytes(item.size)}
                  <button onClick={() => removeAttachment(item.id)} type="button">
                    ×
                  </button>
                </span>
              ))}
            </div>
          ) : null}

          {slashSuggestions.length ? (
            <div className="slash-suggestions">
              {slashSuggestions.map((shortcut) => (
                <button
                  className="slash-item"
                  key={shortcut.command}
                  onClick={() => setDraft(shortcut.prompt)}
                  type="button"
                >
                  <span>{shortcut.label}</span>
                  <small>{shortcut.description}</small>
                </button>
              ))}
            </div>
          ) : null}

          <div className="composer-input-row">
            <div className="composer-input-wrap">
              <textarea
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    if ((!draft.trim() && !attachments.length) || isStreaming) {
                      return;
                    }
                    void sendQuestion(draft);
                  }
                }}
                placeholder={
                  runMode === "demo"
                    ? "输入问题，直接体验完整聊天流程"
                    : "输入问题，连接真实 API 后即可联调"
                }
                ref={textareaRef}
                rows={1}
                value={draft}
              />
            </div>
            <button disabled={isStreaming || (!draft.trim() && !attachments.length)} type="submit">
              {isStreaming ? "生成中..." : "发送"}
            </button>
          </div>

          {speechError ? <p className="composer-footnote error">{speechError}</p> : null}
          {attachments.length ? (
            <p className="composer-footnote">
              演示模式会把附件元信息注入本地上下文；真实 API 模式会先上传文件，再基于解析片段检索并回传引用。
            </p>
          ) : null}
        </form>
      </main>
    </div>
  );
}
