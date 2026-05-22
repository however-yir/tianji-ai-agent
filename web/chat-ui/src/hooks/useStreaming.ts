import { useCallback, useEffect, useRef } from "react";
import type {
  AttachmentItem,
  BannerState,
  ChatEventPayload,
  ChatMessage,
  EvidenceCard,
  MemoryHit,
  RouteResult,
} from "../types";
import { streamChatEvents, requestJson } from "../api/client";
import {
  buildDemoReply,
} from "../utils/demo";
import {
  createBanner,
  errorToMessage,
  extractAttachmentIds,
  normalizeText,
  stripMarkdown,
  toReferences,
  toToolSummary,
} from "../utils/helpers";

type SetAssistantPatch = Partial<ChatMessage> | ((message: ChatMessage) => Partial<ChatMessage>);

export type UseStreamingOptions = {
  setAssistantState: (sessionId: string, messageId: string, patch: SetAssistantPatch) => void;
  setBanner: (banner: BannerState | null) => void;
  setIsStreaming: (value: boolean) => void;
  token: string;
  activeSessionId: string;
  runMode: "demo" | "api";
  sessionMeta: Record<string, { title?: string }>;
  activeTitle: string;
  getSessionMessages: (sessionId: string) => ChatMessage[];
  upsertApiSessionSummary: (session: { id: string; title: string; updatedAt: string; source: "api"; lastSnippet: string }) => void;
};

export function useStreaming(options: UseStreamingOptions) {
  const optionsRef = useRef(options);
  useEffect(() => {
    optionsRef.current = options;
  });

  const abortRef = useRef<AbortController | null>(null);
  const currentStreamRef = useRef<{ sessionId: string; messageId: string } | null>(null);

  const handleStopStreaming = useCallback(async () => {
    const { setAssistantState, setIsStreaming, token, activeSessionId, runMode } = optionsRef.current;
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
  }, []);

  const runDemoStreaming = useCallback(
    async (sessionId: string, messageId: string, question: string, currentAttachments: AttachmentItem[]) => {
      const { setAssistantState, setBanner, setIsStreaming } = optionsRef.current;
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
          routeResult: reply.routeResult || null,
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
    [],
  );

  const runApiStreaming = useCallback(
    async (
      sessionId: string,
      messageId: string,
      question: string,
      attachmentIds: string[] = [],
    ) => {
      const {
        setAssistantState, setBanner, setIsStreaming,
        token, sessionMeta, activeTitle,
        getSessionMessages, upsertApiSessionSummary,
      } = optionsRef.current;
      const controller = new AbortController();
      abortRef.current = controller;
      currentStreamRef.current = { sessionId, messageId };
      setIsStreaming(true);

      try {
        await streamChatEvents({ question, sessionId, attachmentIds }, token, controller.signal, (event: ChatEventPayload) => {
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
          if (event.eventType === 1004) {
            const route = (event.eventData && typeof event.eventData === "object"
              ? event.eventData
              : { intent: String(event.eventData ?? "") }) as RouteResult;
            setAssistantState(sessionId, messageId, { routeResult: route });
            return;
          }
          if (event.eventType === 1006) {
            const evidenceList = (event.eventData && typeof event.eventData === "object"
              ? [event.eventData as EvidenceCard]
              : []) as EvidenceCard[];
            if (evidenceList.length > 0) {
              setAssistantState(sessionId, messageId, (prev) => ({
                evidence: [...(prev.evidence ?? []), ...evidenceList],
              }));
            }
            return;
          }
          if (event.eventType === 1007) {
            const hits = (event.eventData && typeof event.eventData === "object"
              ? [event.eventData as MemoryHit]
              : []) as MemoryHit[];
            if (hits.length > 0) {
              setAssistantState(sessionId, messageId, (prev) => ({
                memoryHits: [...(prev.memoryHits ?? []), ...hits],
              }));
            }
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
    [],
  );

  return {
    abortRef,
    currentStreamRef,
    handleStopStreaming,
    runDemoStreaming,
    runApiStreaming,
  };
}
