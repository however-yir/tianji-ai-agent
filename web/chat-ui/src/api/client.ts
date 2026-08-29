import type {
  AttachmentItem,
  AttachmentUploadResponseItem,
  ChatEventPayload,
} from "../types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api";
const SSE_EVENT_TYPES = new Set([1001, 1002, 1003, 1004, 1005, 1006, 1007]);

// ---------------------------------------------------------------------------
// Token storage: sessionStorage + TTL
// ---------------------------------------------------------------------------

const TOKEN_KEY = "tianji.chat.api-token";
const TOKEN_TTL_MS = 2 * 60 * 60 * 1000; // 2 hours

type StoredToken = { value: string; expiresAt: number };

export function saveToken(value: string): void {
  if (!value) {
    sessionStorage.removeItem(TOKEN_KEY);
    return;
  }
  const entry: StoredToken = { value, expiresAt: Date.now() + TOKEN_TTL_MS };
  sessionStorage.setItem(TOKEN_KEY, JSON.stringify(entry));
}

export function loadToken(): string {
  const raw = sessionStorage.getItem(TOKEN_KEY);
  if (!raw) return "";
  try {
    const entry: StoredToken = JSON.parse(raw);
    if (Date.now() > entry.expiresAt) {
      sessionStorage.removeItem(TOKEN_KEY);
      return "";
    }
    return entry.value;
  } catch {
    sessionStorage.removeItem(TOKEN_KEY);
    return "";
  }
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}

// ---------------------------------------------------------------------------
// Generic JSON request
// ---------------------------------------------------------------------------

class HttpError extends Error {
  status: number;
  payload: unknown;
  constructor(message: string, status: number, payload: unknown) {
    super(message);
    this.name = "HttpError";
    this.status = status;
    this.payload = payload;
  }
}

function normalizePayload<T>(raw: unknown): T {
  if (raw && typeof raw === "object" && "data" in (raw as Record<string, unknown>)) {
    return (raw as { data: T }).data;
  }
  return raw as T;
}

export async function requestJson<T>(
  path: string,
  init: RequestInit = {},
  token?: string,
  timeoutMs = 15_000,
): Promise<T> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const headers: Record<string, string> = {
      Accept: "application/json",
      ...(init.headers as Record<string, string>),
    };
    if (token) {
      headers.Authorization = token.startsWith("Bearer ") ? token : `Bearer ${token}`;
    }
    if (init.body && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }
    const res = await fetch(API_BASE_URL + path, {
      ...init,
      headers,
      signal: controller.signal,
    });
    if (!res.ok) {
      let payload: unknown = null;
      try {
        payload = await res.json();
      } catch {
        /* ignore */
      }
      const message =
        res.status === 401 ? "接口返回 401，请检查 Token 是否正确或已过期。" : `服务端出现异常（${res.status}），请稍后再试。`;
      throw new HttpError(message, res.status, payload);
    }
    const text = await res.text();
    if (!text) return undefined as T;
    return normalizePayload<T>(JSON.parse(text));
  } finally {
    window.clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// SSE streaming (POST → ReadableStream)
// ---------------------------------------------------------------------------

export async function streamChatEvents(
  body: {
    question: string;
    sessionId: string;
    attachmentIds?: string[];
    provider?: string;
    model?: string;
    temperature?: number;
  },
  token: string,
  signal: AbortSignal | undefined,
  onEvent: (event: ChatEventPayload) => void,
): Promise<void> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "text/event-stream",
  };
  if (token) {
    headers.Authorization = token.startsWith("Bearer ") ? token : `Bearer ${token}`;
  }
  const res = await fetch(API_BASE_URL + "/chat", {
    method: "POST",
    headers,
    body: JSON.stringify(body),
    signal,
  });
  if (!res.ok || !res.body) {
    const message =
      res.status === 401 ? "接口返回 401，请检查 Token 是否正确或已过期。" : `服务端出现异常（${res.status}），请稍后再试。`;
    throw new Error(message);
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let stopReceived = false;
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split("\n\n");
    buffer = blocks.pop() || "";
    for (const block of blocks) {
      const dataLine = block
        .split("\n")
        .filter((l) => l.startsWith("data:"))
        .map((l) => l.slice(5).trim())
        .join("");
      if (!dataLine || dataLine === "[DONE]") continue;
      try {
        const event = JSON.parse(dataLine) as ChatEventPayload;
        if (!isValidChatEvent(event)) continue;
        onEvent(event);
        if (event.eventType === 1002) {
          stopReceived = true;
          await reader.cancel();
          return;
        }
      } catch {
        /* ignore malformed chunk */
      }
    }
  }
  if (!stopReceived) {
    throw new Error("服务端流式响应未按协议返回 STOP，请稍后重试。");
  }
}

export function isValidChatEvent(event: ChatEventPayload): boolean {
  if (!SSE_EVENT_TYPES.has(event.eventType ?? 0)) return false;
  const payload = event.eventData;
  switch (event.eventType) {
    case 1001:
      return typeof payload === "string" && payload.length > 0;
    case 1002:
      return true;
    case 1003:
      return isRecord(payload);
    case 1004:
      return payload !== null && typeof payload === "object";
    case 1005:
    case 1006:
    case 1007:
      return Array.isArray(payload) || isRecord(payload);
    default:
      return false;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

// ---------------------------------------------------------------------------
// Attachment upload
// ---------------------------------------------------------------------------

export async function uploadAttachments(
  items: AttachmentItem[],
  token: string,
): Promise<{ uploadedIds: string[]; uploadedItems: AttachmentItem[] }> {
  const form = new FormData();
  for (const item of items) {
    if (item.file) {
      form.append("files", item.file, item.name);
    }
  }
  const headers: Record<string, string> = {};
  if (token) {
    headers.Authorization = token.startsWith("Bearer ") ? token : `Bearer ${token}`;
  }
  const res = await fetch(API_BASE_URL + "/attachment/upload", {
    method: "POST",
    headers,
    body: form,
  });
  if (!res.ok) {
    throw new Error(`Upload failed: ${res.status}`);
  }
  const json = await res.json();
  const list: AttachmentUploadResponseItem[] =
    (json && typeof json === "object" && "data" in json
      ? (json as { data: AttachmentUploadResponseItem[] }).data
      : json) || [];
  const uploadedIds: string[] = [];
  const uploadedItems: AttachmentItem[] = [];
  for (const entry of list) {
    if (entry.attachmentId) {
      uploadedIds.push(entry.attachmentId);
      uploadedItems.push({
        id: entry.attachmentId,
        name: entry.name || "未命名文件",
        size: entry.size || 0,
        type: entry.contentType || "application/octet-stream",
        uploadId: entry.attachmentId,
        previewText: entry.previewText,
        chunkCount: entry.chunkCount,
      });
    }
  }
  return { uploadedIds, uploadedItems };
}
