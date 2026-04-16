import { isValidElement, useEffect, useMemo, useRef, useState } from "react";
import type { ComponentPropsWithoutRef, FormEvent, KeyboardEvent, ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";
import "highlight.js/styles/github.css";
import "./App.css";

type MessageRole = "user" | "assistant";
type HistorySessionGroup = Record<string, ChatSessionItem[]>;

interface ChatSessionItem {
  sessionId: string;
  title: string;
  updateTime?: string;
}

interface SessionInfo {
  sessionId: string;
  title: string;
  describe?: string;
  examples?: ExampleItem[];
}

interface ExampleItem {
  title: string;
  describe: string;
}

interface MessageItem {
  id: string;
  role: MessageRole;
  content: string;
  params?: Record<string, unknown>;
  pending?: boolean;
  retryable?: boolean;
  failed?: boolean;
  question?: string;
}

interface ChatEvent {
  eventType: number;
  eventData?: unknown;
}

interface ShortcutCommand {
  id: string;
  trigger: string;
  label: string;
  prompt: string;
}

const EVENT_DATA = 1001;
const EVENT_STOP = 1002;
const EVENT_PARAM = 1003;

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/$/, "");
const LOCAL_TOKEN_KEY = "tj-chat-ui-token";

class ApiHttpError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

const SHORTCUTS: ShortcutCommand[] = [
  {
    id: "summary",
    trigger: "/summary",
    label: "总结会话",
    prompt: "请用 3-5 条总结当前对话的核心结论，并给出建议的下一步行动。",
  },
  {
    id: "todo",
    trigger: "/todo",
    label: "提取待办",
    prompt: "请基于当前上下文整理待办清单，按优先级排序并标注预估耗时。",
  },
  {
    id: "spec",
    trigger: "/spec",
    label: "输出方案",
    prompt: "请把当前需求整理成可执行方案：目标、范围、关键设计、风险和里程碑。",
  },
  {
    id: "review",
    trigger: "/review",
    label: "审查建议",
    prompt: "请从正确性、性能、可维护性三个维度给出审查建议，并按优先级排序。",
  },
  {
    id: "translate",
    trigger: "/translate",
    label: "中英互译",
    prompt: "请将以下内容做准确专业的中英互译，并保留专业术语：",
  },
];

function createId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function isWrappedResponse(value: unknown): value is { code: number; msg: string; data: unknown } {
  return Boolean(
    value &&
      typeof value === "object" &&
      "code" in value &&
      "msg" in value &&
      "data" in value,
  );
}

async function unwrapApiResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new ApiHttpError(response.status, text || `请求失败: ${response.status}`);
  }
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  const parsed = JSON.parse(text) as unknown;
  if (isWrappedResponse(parsed)) {
    return parsed.data as T;
  }
  return parsed as T;
}

function parseHistorySessions(data: HistorySessionGroup): ChatSessionItem[] {
  return Object.entries(data || {}).flatMap(([, sessions]) => sessions || []);
}

function normalizeMessageRole(type: unknown): MessageRole {
  if (type === 1 || type === "USER") {
    return "user";
  }
  return "assistant";
}

function toMessageList(data: unknown): MessageItem[] {
  if (!Array.isArray(data)) {
    return [];
  }

  const list: MessageItem[] = [];
  for (const item of data) {
    if (!item || typeof item !== "object") {
      continue;
    }
    const raw = item as Record<string, unknown>;
    const role = normalizeMessageRole(raw.type);
    list.push({
      id: createId("msg"),
      role,
      content: String(raw.content ?? ""),
      params:
        raw.params && typeof raw.params === "object"
          ? (raw.params as Record<string, unknown>)
          : undefined,
      retryable: role === "assistant",
    });
  }
  return list;
}

function parseStreamEvent(raw: string): ChatEvent | null {
  if (!raw.trim()) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (isWrappedResponse(parsed)) {
      return parsed.data as ChatEvent;
    }
    if (parsed && typeof parsed === "object" && "eventType" in parsed) {
      return parsed as ChatEvent;
    }
    return { eventType: EVENT_DATA, eventData: raw };
  } catch {
    return { eventType: EVENT_DATA, eventData: raw };
  }
}

function formatTimeLabel(value?: string): string {
  if (!value) {
    return "";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "";
  }
  return parsed.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function isAuthError(error: unknown): error is ApiHttpError {
  return error instanceof ApiHttpError && (error.status === 401 || error.status === 403);
}

function extractNodeText(node: ReactNode): string {
  if (typeof node === "string" || typeof node === "number") {
    return String(node);
  }
  if (Array.isArray(node)) {
    return node.map((item) => extractNodeText(item)).join("");
  }
  if (isValidElement<{ children?: ReactNode }>(node)) {
    return extractNodeText(node.props.children);
  }
  return "";
}

async function copyText(text: string): Promise<boolean> {
  if (!text) {
    return false;
  }
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

function splitSlashInput(raw: string): { token: string; tail: string } | null {
  const trimmed = raw.trimStart();
  if (!trimmed.startsWith("/")) {
    return null;
  }
  const [first, ...rest] = trimmed.split(/\s+/);
  return {
    token: first.toLowerCase(),
    tail: rest.join(" ").trim(),
  };
}

function findRetryQuestion(messageId: string, snapshot: MessageItem[]): string | null {
  const index = snapshot.findIndex((item) => item.id === messageId);
  if (index === -1) {
    return null;
  }
  const current = snapshot[index];
  if (current.question?.trim()) {
    return current.question.trim();
  }
  for (let i = index - 1; i >= 0; i -= 1) {
    const candidate = snapshot[i];
    if (candidate.role === "user" && candidate.content.trim()) {
      return candidate.content.trim();
    }
  }
  return null;
}

function MarkdownPre({ children }: ComponentPropsWithoutRef<"pre">) {
  const [copied, setCopied] = useState(false);
  const rawCode = useMemo(() => extractNodeText(children).replace(/\n$/, ""), [children]);

  async function onCopyCode() {
    const success = await copyText(rawCode);
    if (success) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1400);
    }
  }

  return (
    <div className="md-pre-wrap">
      <button className="copy-code-btn" onClick={() => void onCopyCode()} type="button">
        {copied ? "已复制" : "复制代码"}
      </button>
      <pre>{children}</pre>
    </div>
  );
}

function MarkdownMessage({ content }: { content: string }) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      rehypePlugins={[rehypeHighlight]}
      components={{
        pre(props) {
          return <MarkdownPre>{props.children}</MarkdownPre>;
        },
        code(props) {
          const className = props.className || "";
          if (className.startsWith("language-")) {
            return <code className={className}>{props.children}</code>;
          }
          return <code className="inline-code">{props.children}</code>;
        },
        a(props) {
          const href = props.href || "#";
          return (
            <a href={href} target="_blank" rel="noreferrer">
              {props.children}
            </a>
          );
        },
      }}
    >
      {content}
    </ReactMarkdown>
  );
}

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem(LOCAL_TOKEN_KEY) || "");
  const [authRequired, setAuthRequired] = useState(false);
  const [sessionInfo, setSessionInfo] = useState<SessionInfo | null>(null);
  const [sessionList, setSessionList] = useState<ChatSessionItem[]>([]);
  const [sessionQuery, setSessionQuery] = useState("");
  const [hotExamples, setHotExamples] = useState<ExampleItem[]>([]);
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [inputValue, setInputValue] = useState("");
  const [copiedMessageId, setCopiedMessageId] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [bootLoading, setBootLoading] = useState(true);

  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const streamAbortRef = useRef<AbortController | null>(null);

  const canSend = useMemo(() => inputValue.trim().length > 0 && !isSending, [inputValue, isSending]);

  const filteredSessionList = useMemo(() => {
    const query = sessionQuery.trim().toLowerCase();
    if (!query) {
      return sessionList;
    }
    return sessionList.filter((item) => item.title.toLowerCase().includes(query));
  }, [sessionList, sessionQuery]);

  const slashSuggestions = useMemo(() => {
    const slash = splitSlashInput(inputValue);
    if (!slash) {
      return [];
    }
    return SHORTCUTS.filter((command) => command.trigger.startsWith(slash.token));
  }, [inputValue]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, isSending]);

  useEffect(() => {
    localStorage.setItem(LOCAL_TOKEN_KEY, token);
  }, [token]);

  useEffect(() => {
    void bootstrap();

    return () => {
      streamAbortRef.current?.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleApiError(error: unknown, fallback: string): string {
    if (isAuthError(error)) {
      setAuthRequired(true);
      return "未认证（401）：请在左侧 API Token 输入 Bearer Token 后点击“使用 Token 重试”。";
    }
    return error instanceof Error ? error.message : fallback;
  }

  async function bootstrap() {
    setBootLoading(true);
    setErrorMessage("");
    try {
      await Promise.all([loadSessionHistory(), loadHotExamples()]);
      setAuthRequired(false);
    } catch (error) {
      setErrorMessage(handleApiError(error, "初始化失败"));
    } finally {
      setBootLoading(false);
    }
  }

  function buildHeaders(json = true): HeadersInit {
    const headers: Record<string, string> = {};
    if (json) {
      headers["Content-Type"] = "application/json";
    }
    if (token.trim()) {
      headers.Authorization = `Bearer ${token.trim()}`;
    }
    return headers;
  }

  function resolveCommandInput(raw: string): string {
    const slash = splitSlashInput(raw);
    if (!slash) {
      return raw;
    }
    const command = SHORTCUTS.find((item) => item.trigger === slash.token);
    if (!command) {
      return raw;
    }
    if (!slash.tail) {
      return command.prompt;
    }
    return `${command.prompt}\n\n${slash.tail}`;
  }

  function applyShortcut(command: ShortcutCommand, tail = "") {
    const nextInput = tail ? `${command.prompt}\n\n${tail}` : command.prompt;
    setInputValue(nextInput);
    setTimeout(() => textareaRef.current?.focus(), 0);
  }

  async function loadHotExamples() {
    const response = await fetch(`${API_BASE_URL}/session/hot?n=6`, {
      method: "GET",
      headers: buildHeaders(false),
    });
    const data = await unwrapApiResponse<ExampleItem[]>(response);
    setHotExamples(Array.isArray(data) ? data : []);
  }

  async function loadSessionHistory() {
    const response = await fetch(`${API_BASE_URL}/session/history`, {
      method: "GET",
      headers: buildHeaders(false),
    });
    const data = await unwrapApiResponse<HistorySessionGroup>(response);
    const sessions = parseHistorySessions(data);
    setSessionList(sessions);
  }

  async function createSession(): Promise<SessionInfo> {
    const response = await fetch(`${API_BASE_URL}/session?n=3`, {
      method: "POST",
      headers: buildHeaders(false),
    });
    return unwrapApiResponse<SessionInfo>(response);
  }

  async function loadSessionMessages(sessionId: string) {
    setErrorMessage("");
    try {
      const response = await fetch(`${API_BASE_URL}/session/${sessionId}`, {
        method: "GET",
        headers: buildHeaders(false),
      });
      const data = await unwrapApiResponse<unknown>(response);
      setMessages(toMessageList(data));
      setAuthRequired(false);

      const active = sessionList.find((item) => item.sessionId === sessionId);
      setSessionInfo({
        sessionId,
        title: active?.title || "历史对话",
      });
    } catch (error) {
      setErrorMessage(handleApiError(error, "读取会话失败"));
    }
  }

  async function streamAssistantMessage(sessionId: string, question: string, assistantMessageId: string) {
    const abortController = new AbortController();
    streamAbortRef.current = abortController;

    await streamChat(
      {
        question,
        sessionId,
      },
      (event) => {
        if (event.eventType === EVENT_DATA) {
          setMessages((prev) =>
            prev.map((item) =>
              item.id === assistantMessageId
                ? {
                    ...item,
                    content: `${item.content}${String(event.eventData ?? "")}`,
                    pending: false,
                    failed: false,
                    question,
                    retryable: true,
                  }
                : item,
            ),
          );
        }

        if (event.eventType === EVENT_PARAM) {
          setMessages((prev) =>
            prev.map((item) =>
              item.id === assistantMessageId
                ? {
                    ...item,
                    params:
                      event.eventData && typeof event.eventData === "object"
                        ? (event.eventData as Record<string, unknown>)
                        : undefined,
                  }
                : item,
            ),
          );
        }

        if (event.eventType === EVENT_STOP) {
          setMessages((prev) =>
            prev.map((item) =>
              item.id === assistantMessageId
                ? {
                    ...item,
                    pending: false,
                    question,
                    retryable: true,
                  }
                : item,
            ),
          );
        }
      },
      abortController.signal,
    );
  }

  async function submitMessage(question?: string) {
    const content = resolveCommandInput(question ?? inputValue).trim();
    if (!content || isSending) {
      return;
    }

    setErrorMessage("");
    setInputValue("");

    let currentSession = sessionInfo;

    try {
      if (!currentSession?.sessionId) {
        currentSession = await createSession();
        setSessionInfo(currentSession);
      }

      const userMessage: MessageItem = {
        id: createId("user"),
        role: "user",
        content,
      };

      const assistantMessageId = createId("assistant");
      const assistantMessage: MessageItem = {
        id: assistantMessageId,
        role: "assistant",
        content: "",
        pending: true,
        retryable: true,
        question: content,
      };

      setMessages((prev) => [...prev, userMessage, assistantMessage]);
      setIsSending(true);

      await streamAssistantMessage(currentSession.sessionId, content, assistantMessageId);
      await loadSessionHistory();
      setAuthRequired(false);
    } catch (error) {
      setErrorMessage(handleApiError(error, "发送消息失败"));
      setMessages((prev) =>
        prev.map((item) =>
          item.pending
            ? {
                ...item,
                pending: false,
                failed: true,
                content: item.content || "生成失败，请点击重试",
                retryable: true,
              }
            : item,
        ),
      );
    } finally {
      setIsSending(false);
      streamAbortRef.current = null;
    }
  }

  async function retryMessage(messageId: string) {
    if (isSending || !sessionInfo?.sessionId) {
      return;
    }

    const question = findRetryQuestion(messageId, messages);
    if (!question) {
      setErrorMessage("未找到可重试的问题上下文");
      return;
    }

    setErrorMessage("");
    setIsSending(true);
    setMessages((prev) =>
      prev.map((item) =>
        item.id === messageId
          ? {
              ...item,
              content: "",
              params: undefined,
              pending: true,
              failed: false,
              question,
              retryable: true,
            }
          : item,
      ),
    );

    try {
      await streamAssistantMessage(sessionInfo.sessionId, question, messageId);
      await loadSessionHistory();
      setAuthRequired(false);
    } catch (error) {
      setErrorMessage(handleApiError(error, "重试失败"));
      setMessages((prev) =>
        prev.map((item) =>
          item.id === messageId
            ? {
                ...item,
                pending: false,
                failed: true,
                content: item.content || "重试失败，请稍后再试",
              }
            : item,
        ),
      );
    } finally {
      setIsSending(false);
      streamAbortRef.current = null;
    }
  }

  async function copyMessage(message: MessageItem) {
    const success = await copyText(message.content);
    if (!success) {
      return;
    }
    setCopiedMessageId(message.id);
    window.setTimeout(() => {
      setCopiedMessageId((prev) => (prev === message.id ? "" : prev));
    }, 1500);
  }

  async function streamChat(
    payload: { question: string; sessionId: string },
    onEvent: (event: ChatEvent) => void,
    signal: AbortSignal,
  ) {
    const response = await fetch(`${API_BASE_URL}/chat`, {
      method: "POST",
      headers: buildHeaders(true),
      body: JSON.stringify(payload),
      signal,
    });

    if (!response.ok || !response.body) {
      throw new Error(`流式请求失败: ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    const flush = (block: string) => {
      const dataLines = block
        .split(/\r?\n/)
        .filter((line) => line.startsWith("data:"))
        .map((line) => line.slice(5).trim());

      if (!dataLines.length) {
        return;
      }

      const raw = dataLines.join("\n");
      if (raw === "[DONE]") {
        onEvent({ eventType: EVENT_STOP });
        return;
      }

      const parsed = parseStreamEvent(raw);
      if (parsed) {
        onEvent(parsed);
      }
    };

    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });

      let index = buffer.indexOf("\n\n");
      while (index !== -1) {
        const block = buffer.slice(0, index);
        buffer = buffer.slice(index + 2);
        flush(block);
        index = buffer.indexOf("\n\n");
      }
    }

    if (buffer.trim()) {
      flush(buffer);
    }
  }

  async function onCreateNewChat() {
    streamAbortRef.current?.abort();
    setIsSending(false);
    setMessages([]);
    setErrorMessage("");

    try {
      const created = await createSession();
      setSessionInfo(created);
      await loadSessionHistory();
      setAuthRequired(false);
    } catch (error) {
      setErrorMessage(handleApiError(error, "新建会话失败"));
    }
  }

  async function onStopGeneration() {
    if (!sessionInfo?.sessionId || !isSending) {
      return;
    }

    try {
      await fetch(`${API_BASE_URL}/chat/stop?sessionId=${encodeURIComponent(sessionInfo.sessionId)}`, {
        method: "POST",
        headers: buildHeaders(false),
      });
    } catch {
      // ignore stop API failures; abort still works for UI
    }

    streamAbortRef.current?.abort();
    setIsSending(false);
    setMessages((prev) => prev.map((item) => (item.pending ? { ...item, pending: false } : item)));
  }

  function onTextareaKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      void submitMessage();
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submitMessage();
  }

  const sessionCountLabel = sessionQuery.trim()
    ? `${filteredSessionList.length}/${sessionList.length}`
    : String(sessionList.length);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <p className="brand-kicker">Tianji Agent</p>
          <h1>对话工作台</h1>
          <p className="brand-desc">支持 Markdown、代码高亮、消息重试和快捷指令的完整聊天前端。</p>
        </div>

        <button className="new-chat-btn" onClick={() => void onCreateNewChat()}>
          + 新建对话
        </button>

        <div className="sidebar-group">
          <div className="group-title-row">
            <h2>历史会话</h2>
            <span>{sessionCountLabel}</span>
          </div>

          <div className="history-search-wrap">
            <input
              className="history-search"
              value={sessionQuery}
              onChange={(event) => setSessionQuery(event.target.value)}
              placeholder="搜索会话标题"
            />
            {sessionQuery.trim() ? (
              <button className="clear-search-btn" onClick={() => setSessionQuery("")} type="button">
                清空
              </button>
            ) : null}
          </div>

          <div className="history-list">
            {sessionList.length === 0 && <p className="placeholder">暂无历史会话</p>}
            {sessionList.length > 0 && filteredSessionList.length === 0 ? (
              <p className="placeholder">没有匹配的会话</p>
            ) : null}
            {filteredSessionList.map((item) => (
              <button
                key={item.sessionId}
                className={`history-item ${sessionInfo?.sessionId === item.sessionId ? "active" : ""}`}
                onClick={() => void loadSessionMessages(item.sessionId)}
              >
                <span className="history-title">{item.title}</span>
                <span className="history-time">{formatTimeLabel(item.updateTime)}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="sidebar-group token-panel">
          <h2>API Token</h2>
          <input
            type="password"
            value={token}
            onChange={(event) => setToken(event.target.value)}
            placeholder="可选：Bearer Token"
          />
          <p className="token-tip">如果后端开启鉴权，请填写。</p>
          <button className="token-action-btn" type="button" onClick={() => void bootstrap()}>
            使用 Token 重试
          </button>
        </div>
      </aside>

      <main className="chat-main">
        <header className="chat-header">
          <div>
            <p className="header-label">当前会话</p>
            <h2>{sessionInfo?.title || "新的对话"}</h2>
          </div>
          {isSending ? (
            <button className="stop-btn" onClick={() => void onStopGeneration()}>
              停止生成
            </button>
          ) : (
            <span className="status-pill">空闲</span>
          )}
        </header>

        <section className="message-panel">
          {bootLoading ? <p className="placeholder loading-tip">加载中...</p> : null}

          {authRequired ? (
            <div className="auth-hint">
              当前接口需要登录凭证。请在左下角 `API Token` 填入 `Bearer Token`，然后点击“使用 Token 重试”。
            </div>
          ) : null}

          {!bootLoading && messages.length === 0 ? (
            <div className="welcome-panel">
              <h3>开始一段高质量对话</h3>
              <p>支持会话记忆、Markdown 输出、代码高亮、参数回传和流式响应。</p>
              <div className="example-grid">
                {hotExamples.length === 0 && <p className="placeholder">暂无推荐问题</p>}
                {hotExamples.map((example, index) => (
                  <button
                    key={`${example.title}-${index}`}
                    className="example-card"
                    onClick={() => void submitMessage(example.describe || example.title)}
                  >
                    <span>{example.title}</span>
                    <small>{example.describe}</small>
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          <div className="message-list">
            {messages.map((message) => {
              const retryQuestion = message.question || findRetryQuestion(message.id, messages);
              const retryDisabled = !retryQuestion || isSending || !sessionInfo?.sessionId;
              return (
                <article key={message.id} className={`message-row ${message.role}`}>
                  <div className="avatar">{message.role === "user" ? "你" : "AI"}</div>
                  <div className="bubble-wrap">
                    <div
                      className={`bubble ${message.pending ? "pending" : ""} ${message.failed ? "failed" : ""}`}
                    >
                      {message.role === "assistant" ? (
                        <MarkdownMessage
                          content={message.content || (message.pending ? "正在思考中，请稍候..." : "")}
                        />
                      ) : (
                        <span className="plain-text">{message.content}</span>
                      )}
                    </div>

                    {message.params ? (
                      <pre className="params-block">{JSON.stringify(message.params, null, 2)}</pre>
                    ) : null}

                    {message.role === "assistant" && !message.pending ? (
                      <div className="message-actions">
                        <button onClick={() => void copyMessage(message)} type="button">
                          {copiedMessageId === message.id ? "已复制" : "复制"}
                        </button>
                        <button
                          disabled={retryDisabled}
                          onClick={() => void retryMessage(message.id)}
                          type="button"
                        >
                          重试
                        </button>
                      </div>
                    ) : null}
                  </div>
                </article>
              );
            })}
          </div>

          <div ref={bottomRef} />
        </section>

        {errorMessage ? <div className="error-bar">{errorMessage}</div> : null}

        <form className="composer" onSubmit={onSubmit}>
          <div className="shortcut-panel">
            {SHORTCUTS.map((command) => (
              <button
                key={command.id}
                type="button"
                onClick={() => applyShortcut(command)}
                className="shortcut-btn"
              >
                {command.label}
              </button>
            ))}
          </div>

          <div className="composer-input-row">
            <div className="composer-input-wrap">
              <textarea
                ref={textareaRef}
                value={inputValue}
                onChange={(event) => setInputValue(event.target.value)}
                onKeyDown={onTextareaKeyDown}
                placeholder="输入问题，Enter 发送，Shift+Enter 换行；输入 / 触发快捷指令"
                rows={1}
              />

              {slashSuggestions.length > 0 ? (
                <div className="slash-suggestions">
                  {slashSuggestions.map((command) => (
                    <button
                      key={command.id}
                      type="button"
                      className="slash-item"
                      onClick={() => {
                        const slashInput = splitSlashInput(inputValue);
                        applyShortcut(command, slashInput?.tail || "");
                      }}
                    >
                      <span>{command.trigger}</span>
                      <small>{command.label}</small>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>

            <button type="submit" disabled={!canSend}>
              发送
            </button>
          </div>
        </form>
      </main>
    </div>
  );
}
