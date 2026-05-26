import {
  useCallback,
  useDeferredValue,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { ChangeEvent, FormEvent } from "react";
import "./App.css";

import type {
  AttachmentItem,
  BannerState,
  ChatMessage,
  DemoSession,
  DemoStore,
  HistoryApiItem,
  MessageApiItem,
  ModelOption,
  ProvidersResponse,
  RunMode,
  SessionApiResponse,
  SessionMeta,
  SessionSummary,
  SpeechRecognitionInstance,
} from "./types";
import {
  requestJson,
  uploadAttachments,
  loadToken,
  saveToken,
} from "./api/client";
import {
  buildAttachmentContext,
  createBanner,
  createId,
  downloadTextFile,
  errorToMessage,
  extractAttachmentIds,
  extractHarnessTraces,
  findShortcut,
  formatTimestamp,
  formatBytes,
  readStorage,
  resolvePrompt,
  stripMarkdown,
  snapshotAttachments,
  toReferences,
  toToolSummary,
  writeStorage,
  titleFromQuestion,
} from "./utils/helpers";
import { createSeedDemoStore } from "./utils/demo";
import { useStreaming } from "./hooks/useStreaming";
import MessageBubble from "./components/MessageBubble";

const STORAGE_KEYS = {
  mode: "tianji.chat.run-mode",
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


export default function App() {
  const [runMode, setRunMode] = useState<RunMode>(() => readStorage(STORAGE_KEYS.mode, "demo"));
  const [token, setToken] = useState(() => loadToken());
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
  const [providersConfig, setProvidersConfig] = useState<ProvidersResponse | null>(null);
  const [selectedProvider, setSelectedProvider] = useState("");
  const [selectedModel, setSelectedModel] = useState("");
  const [temperature, setTemperature] = useState(0.7);

  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const messageListRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const speechRecognitionRef = useRef<SpeechRecognitionInstance | null>(null);
  const deferredSearch = useDeferredValue(searchText);

  useEffect(() => {
    writeStorage(STORAGE_KEYS.mode, runMode);
  }, [runMode]);

  useEffect(() => {
    saveToken(token);
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
          harnessTraces: extractHarnessTraces(item.params),
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

  const {
    handleStopStreaming,
    runDemoStreaming,
    runApiStreaming,
  } = useStreaming({
    setAssistantState,
    setBanner,
    setIsStreaming,
    token,
    activeSessionId,
    runMode,
    sessionMeta,
    activeTitle: activeTitle || "新的对话",
    getSessionMessages,
    upsertApiSessionSummary,
    selectedProvider,
    selectedModel,
    temperature,
  });

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
      const resolvedPrompt = resolvePrompt(questionText || fallbackPrompt, SHORTCUTS);
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
      const userMessage: ChatMessage = {
        id: createId("user"),
        role: "user",
        content: resolvedPrompt,
        createdAt: new Date().toISOString(),
        status: "done",
        attachments: messageAttachments,
        attachmentIds,
      };

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

  const connectApi = useCallback(async () => {
    setApiConnectRequested(true);
    setBanner(createBanner("info", "正在尝试连接真实 API..."));
    try {
      const config = await requestJson<ProvidersResponse>("/chat/providers", { method: "GET" }, token);
      setProvidersConfig(config);
      if (config && !selectedProvider) {
        setSelectedProvider(config.defaultProvider);
        const defaultProviderCfg = config.providers[config.defaultProvider];
        if (defaultProviderCfg) {
          setSelectedModel(defaultProviderCfg.defaultModel);
        }
        setTemperature(config.defaultTemperature);
      }
    } catch {
      // providers endpoint is optional; silently ignore
    }
  }, [token, selectedProvider]);

  const activeSessionEmpty = activeMessages.length === 0;
  const showAuthCard = runMode === "api" && !apiConnectRequested && !token.trim();
  const currentShortcut = findShortcut(draft, SHORTCUTS);

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

        {runMode === "api" && providersConfig ? (
          <section className="sidebar-group model-config-section">
            <div className="group-title-row">
              <h2>模型设置</h2>
            </div>
            <div className="model-config-inner">
              <label className="config-label">
                供应商
                <select
                  value={selectedProvider}
                  onChange={(e) => {
                    const provider = e.target.value;
                    setSelectedProvider(provider);
                    const cfg = providersConfig.providers[provider];
                    if (cfg) setSelectedModel(cfg.defaultModel);
                  }}
                >
                  {Object.entries(providersConfig.providers).map(([key, cfg]) => (
                    <option key={key} value={key}>{cfg.label}</option>
                  ))}
                </select>
              </label>
              {providersConfig.providers[selectedProvider] ? (
                <label className="config-label">
                  模型
                  <select
                    value={selectedModel}
                    onChange={(e) => setSelectedModel(e.target.value)}
                  >
                    {providersConfig.providers[selectedProvider].models.map((m: ModelOption) => (
                      <option key={m.name} value={m.name}>{m.label}</option>
                    ))}
                  </select>
                </label>
              ) : null}
              <label className="config-label">
                温度 · {temperature.toFixed(1)}
                <input
                  type="range"
                  min="0"
                  max="2"
                  step="0.1"
                  value={temperature}
                  onChange={(e) => setTemperature(parseFloat(e.target.value))}
                />
              </label>
            </div>
          </section>
        ) : null}

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
              <MessageBubble
                key={message.id}
                message={message}
                isStreaming={isStreaming}
                onCopy={handleCopyMessage}
                onRetry={handleRetry}
              />
            ))}
          </div>
        </section>

        {banner ? (
          <div className={`banner banner-${banner.tone}`}>
            {banner.message}
          </div>
        ) : null}

        <div className="quick-bubbles">
          {activeSessionEmpty ? DEMO_EXAMPLES.slice(0, 3).map((ex) => (
            <button className="quick-bubble" key={ex.title} onClick={() => setDraft(ex.describe)} type="button">
              {ex.title}
            </button>
          )) : null}
        </div>

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
