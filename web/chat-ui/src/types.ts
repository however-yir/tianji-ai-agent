export type RunMode = "demo" | "api";
export type MessageRole = "user" | "assistant";
export type MessageStatus = "done" | "streaming" | "failed";
export type BannerTone = "info" | "warning" | "error";

// ---------- Model provider configuration ----------

export type ModelOption = {
  name: string;
  label: string;
};

export type ProviderConfig = {
  label: string;
  defaultModel: string;
  models: ModelOption[];
};

export type ProvidersResponse = {
  defaultProvider: string;
  defaultTemperature: number;
  providers: Record<string, ProviderConfig>;
};

// --------------------------------------------------

export type AttachmentItem = {
  id: string;
  name: string;
  size: number;
  type: string;
  file?: File;
  uploadId?: string;
  previewText?: string;
  chunkCount?: number;
};

export type ReferenceCard = {
  title: string;
  href?: string;
  excerpt?: string;
  tag?: string;
};

export type ToolSummaryItem = {
  label: string;
  value: string;
};

export type CourseCardData = {
  id: string;
  name: string;
  price: number;
  usePeople: string;
  validDuration?: number;
  detail?: string;
};

export type OrderCardData = {
  count: number;
  totalAmount: number;
  discountAmount: number;
  payAmount: number;
  orderId: string;
  couponName?: string;
  businessState?: string;
  stateTrace?: string[];
  nextAction?: string;
};

export type RouteResult = {
  intent?: string;
  confidence?: number;
  reason?: string;
  routeReason?: string;
  nextAgent?: string;
  candidateAgents?: string[];
  needRag?: boolean;
  needMemory?: boolean;
  riskLevel?: string;
  handoffId?: string;
  runId?: string;
  handoffSummary?: string;
};

export type EvidenceCard = {
  sourceType?: string;
  title?: string;
  score?: number;
  reason?: string;
  snippet?: string;
};

export type MemoryHit = {
  type?: string;
  content?: string;
  source?: string;
};

export type AgentHarnessTrace = {
  observationId?: string;
  requestId?: string;
  traceId?: string;
  sessionId?: string;
  agentName?: string;
  toolName?: string;
  actionType?: string;
  status?: "SUCCESS" | "FAILURE" | "DENIED" | string;
  allowed?: boolean;
  success?: boolean;
  policyReason?: string;
  resultField?: string;
  resultType?: string;
  resultSummary?: string;
  errorMessage?: string;
  latencyMillis?: number;
  observedAt?: string;
};

export type ChatMessage = {
  id: string;
  role: MessageRole;
  content: string;
  createdAt: string;
  status: MessageStatus;
  params?: Record<string, unknown> | null;
  references?: ReferenceCard[];
  toolSummary?: ToolSummaryItem[];
  routeResult?: RouteResult | null;
  evidence?: EvidenceCard[];
  memoryHits?: MemoryHit[];
  harnessTraces?: AgentHarnessTrace[];
  originQuestion?: string;
  attachments?: AttachmentItem[];
  attachmentIds?: string[];
  errorText?: string;
};

export type SessionSummary = {
  id: string;
  title: string;
  updatedAt: string;
  source: RunMode;
  lastSnippet?: string;
};

export type DemoSession = {
  id: string;
  title: string;
  updatedAt: string;
  messages: ChatMessage[];
};

export type DemoStore = {
  sessions: DemoSession[];
};

export type SessionMeta = {
  title?: string;
  pinned?: boolean;
  hidden?: boolean;
};

export type BannerState = {
  tone: BannerTone;
  message: string;
};

export type ChatEventPayload = {
  eventType?: number;
  eventData?: unknown;
};

export type SessionApiResponse = {
  sessionId?: string;
  title?: string;
  describe?: string;
  examples?: Array<{ title?: string; describe?: string }>;
};

export type HistoryApiItem = {
  sessionId?: string;
  title?: string;
  updateTime?: string;
};

export type MessageApiItem = {
  type?: string;
  content?: string;
  params?: Record<string, unknown>;
};

export type AttachmentUploadResponseItem = {
  attachmentId?: string;
  name?: string;
  contentType?: string;
  size?: number;
  previewText?: string;
  chunkCount?: number;
};

export type SpeechRecognitionResultLike = {
  transcript: string;
};

export type SpeechRecognitionEventLike = {
  results: ArrayLike<ArrayLike<SpeechRecognitionResultLike>>;
};

export type SpeechRecognitionErrorLike = {
  error?: string;
};

export type SpeechRecognitionInstance = {
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

export type SpeechRecognitionFactory = new () => SpeechRecognitionInstance;

declare global {
  interface Window {
    SpeechRecognition?: SpeechRecognitionFactory;
    webkitSpeechRecognition?: SpeechRecognitionFactory;
  }
}
