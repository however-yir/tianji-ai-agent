import type {
  AttachmentItem,
  BannerState,
  BannerTone,
  ChatMessage,
  CourseCardData,
  OrderCardData,
  ReferenceCard,
  ToolSummaryItem,
} from "../types";

// ---------------------------------------------------------------------------
// ID generation
// ---------------------------------------------------------------------------

let counter = 0;
export function createId(prefix = "id"): string {
  counter += 1;
  return `${prefix}-${Date.now().toString(36)}-${counter}`;
}

// ---------------------------------------------------------------------------
// Storage helpers (generic, works with both localStorage and sessionStorage)
// ---------------------------------------------------------------------------

export function readStorage<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

export function writeStorage(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* quota exceeded */
  }
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

export function formatTimestamp(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const now = new Date();
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();
  const time = date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
  if (sameDay) return time;
  return `${date.getMonth() + 1}/${date.getDate()} ${time}`;
}

export function formatBytes(bytes: number): string {
  if (bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** index;
  return `${value.toFixed(index > 0 ? 1 : 0)} ${units[index]}`;
}

export function formatMoney(amount: number): string {
  return `¥${(amount / 100).toFixed(2)}`;
}

// ---------------------------------------------------------------------------
// Data transforms
// ---------------------------------------------------------------------------

export function snapshotAttachments(items: AttachmentItem[]): AttachmentItem[] {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  return items.map(({ file: _file, ...rest }) => rest);
}

export function stripMarkdown(source: string): string {
  return source
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`[^`]*`/g, " ")
    .replace(/!\[[^\]]*\]\([^)]*\)/g, " ")
    .replace(/\[[^\]]*\]\([^)]*\)/g, " ")
    .replace(/[#>*_~-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function safeJsonParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export function normalizeText(value: unknown): string {
  if (typeof value === "string") return value;
  if (value && typeof value === "object") return JSON.stringify(value);
  return String(value ?? "");
}

export function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
}

// ---------------------------------------------------------------------------
// Card extraction
// ---------------------------------------------------------------------------

export function extractCourseCard(params: Record<string, unknown> | null): CourseCardData | null {
  if (!params) return null;
  const dynamicCourseKey = Object.keys(params).find((key) => key.startsWith("courseInfo_"));
  const raw = params.course ?? params.courseCard ?? params.courseInfo ?? (dynamicCourseKey ? params[dynamicCourseKey] : undefined);
  const record = asRecord(raw);
  if (!record) return null;
  const id = String(record.id ?? record.courseId ?? "");
  const name = String(record.name ?? record.courseName ?? "");
  if (!id || !name) return null;
  return {
    id,
    name,
    price: Number(record.price ?? 0),
    usePeople: String(record.usePeople ?? record.targetAudience ?? ""),
    validDuration: record.validDuration != null ? Number(record.validDuration) : undefined,
    detail: record.detail != null ? String(record.detail) : undefined,
  };
}

export function extractOrderCard(params: Record<string, unknown> | null): OrderCardData | null {
  if (!params) return null;
  const raw = params.order ?? params.orderCard ?? params.orderInfo ?? params.prePlaceOrder;
  const record = asRecord(raw);
  if (!record) return null;
  const orderId = String(record.orderId ?? record.id ?? "");
  if (!orderId) return null;
  return {
    orderId,
    count: Number(record.count ?? record.itemCount ?? 0),
    totalAmount: Number(record.totalAmount ?? 0),
    discountAmount: Number(record.discountAmount ?? 0),
    payAmount: Number(record.payAmount ?? record.totalAmount ?? 0),
    couponName: record.couponName != null ? String(record.couponName) : undefined,
    businessState: record.businessState != null ? String(record.businessState) : undefined,
    stateTrace: Array.isArray(record.stateTrace) ? record.stateTrace.map(String) : undefined,
    nextAction: record.nextAction != null ? String(record.nextAction) : undefined,
  };
}

// ---------------------------------------------------------------------------
// Payload / banner helpers
// ---------------------------------------------------------------------------

export function normalizePayload<T>(raw: unknown): T {
  if (raw && typeof raw === "object" && "data" in (raw as Record<string, unknown>)) {
    return (raw as { data: T }).data;
  }
  return raw as T;
}

export function createBanner(tone: BannerTone, message: unknown): BannerState {
  const text = typeof message === "string" ? message : String(message ?? "未知错误");
  return { tone, message: text };
}

export function errorToMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return String(error ?? "未知错误");
}

// ---------------------------------------------------------------------------
// Tool / reference transforms
// ---------------------------------------------------------------------------

export function toToolSummary(params: Record<string, unknown> | null | undefined): ToolSummaryItem[] {
  if (!params) return [];
  const toolName = params.toolName ?? params.tool;
  const result = params.result ?? params.output;
  const items: ToolSummaryItem[] = [];
  if (toolName) items.push({ label: "工具", value: String(toolName) });
  if (result !== undefined && result !== null) {
    items.push({ label: "结果", value: typeof result === "string" ? result : JSON.stringify(result) });
  }
  const keys = Object.keys(params).filter(
    (k) => !["toolName", "tool", "result", "output", "course", "order", "courseCard", "orderCard", "courseInfo", "orderInfo"].includes(k),
  );
  for (const key of keys.slice(0, 6)) {
    const val = params[key];
    if (val && typeof val !== "object") {
      items.push({ label: key, value: String(val) });
    }
  }
  return items;
}

export function toReferences(params: Record<string, unknown> | null | undefined): ReferenceCard[] {
  if (!params) return [];
  const raw = params.references ?? params.sources ?? params.hits;
  if (!Array.isArray(raw)) return [];
  return raw
    .map((item) => {
      const record = asRecord(item);
      if (!record) return null;
      return {
        title: String(record.title ?? record.name ?? ""),
        href: record.href != null ? String(record.href) : record.url != null ? String(record.url) : undefined,
        excerpt: record.excerpt != null ? String(record.excerpt) : record.snippet != null ? String(record.snippet) : undefined,
        tag: record.tag != null ? String(record.tag) : undefined,
      };
    })
    .filter(Boolean) as ReferenceCard[];
}

export function extractAttachmentIds(params: Record<string, unknown> | null | undefined): string[] {
  if (!params) return [];
  const raw = params.attachmentIds ?? params.attachments;
  if (!Array.isArray(raw)) return [];
  return raw.map(String);
}

// ---------------------------------------------------------------------------
// Shortcut / prompt helpers
// ---------------------------------------------------------------------------

type Shortcut = { command: string; label: string; description: string; prompt: string };

export function findShortcut(text: string, shortcuts: Shortcut[]): Shortcut | undefined {
  const trimmed = text.trim().toLowerCase();
  if (!trimmed.startsWith("/")) return undefined;
  return shortcuts.find((s) => trimmed === s.command || trimmed.startsWith(`${s.command} `));
}

export function resolvePrompt(text: string, shortcuts: Shortcut[]): string {
  const trimmed = text.trim();
  if (!trimmed) return "";
  const shortcut = findShortcut(trimmed, shortcuts);
  if (!shortcut) return trimmed;
  const rest = trimmed.slice(shortcut.command.length).trim();
  if (!rest) return shortcut.prompt;
  return `${shortcut.prompt}\n\n${rest}`;
}

// ---------------------------------------------------------------------------
// Attachment context
// ---------------------------------------------------------------------------

export function buildAttachmentContext(question: string, items: AttachmentItem[]): string {
  if (!items.length) return question;
  const names = items.map((a) => a.name).join("、");
  return `${question}\n\n附件：${names}`;
}

// ---------------------------------------------------------------------------
// Demo helpers
// ---------------------------------------------------------------------------

export function titleFromQuestion(question: string): string {
  const clean = stripMarkdown(question);
  return clean.length > 20 ? clean.slice(0, 20) + "…" : clean || "新的对话";
}

// ---------------------------------------------------------------------------
// File download
// ---------------------------------------------------------------------------

export function downloadTextFile(fileName: string, content: string): void {
  const blob = new Blob([content], { type: "text/markdown;charset=utf-8" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(link.href);
}

export function createDemoMessage(
  role: "user" | "assistant",
  content: string,
  extra?: Partial<ChatMessage>,
): ChatMessage {
  return {
    id: createId(role),
    role,
    content,
    createdAt: new Date().toISOString(),
    status: "done",
    ...extra,
  };
}
