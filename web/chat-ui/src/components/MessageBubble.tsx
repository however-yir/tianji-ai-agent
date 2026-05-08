import { Suspense, lazy, useCallback, useState } from "react";
import type { ChatMessage } from "../types";
import {
  extractCourseCard,
  extractOrderCard,
  formatBytes,
  formatMoney,
  stripMarkdown,
} from "../utils/helpers";

const RichMarkdown = lazy(() => import("./RichMarkdown"));

type MessageBubbleProps = {
  message: ChatMessage;
  isStreaming: boolean;
  onCopy: (content: string) => void;
  onRetry: (message: ChatMessage) => void;
};

export default function MessageBubble({
  message,
  isStreaming,
  onCopy,
  onRetry,
}: MessageBubbleProps) {
  const course = extractCourseCard(message.params ?? null);
  const order = extractOrderCard(message.params ?? null);
  const [speaking, setSpeaking] = useState(false);

  const toggleSpeech = useCallback(() => {
    if (!("speechSynthesis" in window)) return;
    if (speaking) {
      window.speechSynthesis.cancel();
      setSpeaking(false);
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(stripMarkdown(message.content));
    utterance.lang = "zh-CN";
    utterance.onend = () => setSpeaking(false);
    utterance.onerror = () => setSpeaking(false);
    setSpeaking(true);
    window.speechSynthesis.speak(utterance);
  }, [message.content, speaking]);

  return (
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

        {/* Route Result Card */}
        {message.routeResult ? (
          <div className="route-card-v2">
            <div className="route-stepper">
              <div className="route-step active">
                <div className="step-dot"></div>
                <span>用户输入</span>
              </div>
              <div className="route-line"></div>
              <div className="route-step active">
                <div className="step-dot"></div>
                <span>RouteAgent</span>
              </div>
              <div className="route-line"></div>
              <div className="route-step active target">
                <div className="step-dot"></div>
                <span>{message.routeResult.nextAgent ?? message.routeResult.intent ?? "UNKNOWN"}</span>
              </div>
            </div>
            <div className="route-meta-row">
              {message.routeResult.confidence != null ? (
                <span className="route-conf-badge">{(message.routeResult.confidence * 100).toFixed(0)}%</span>
              ) : null}
              {message.routeResult.reason ? <span className="route-reason-text">{message.routeResult.reason}</span> : null}
            </div>
            <div className="route-tag-row">
              {message.routeResult.needRag ? <span className="ev-tag rag">RAG</span> : null}
              {message.routeResult.needMemory ? <span className="ev-tag memory">记忆</span> : null}
              {message.routeResult.riskLevel ? (
                <span className={`ev-tag risk-${(message.routeResult.riskLevel ?? "").toLowerCase()}`}>
                  {message.routeResult.riskLevel}
                </span>
              ) : null}
            </div>
          </div>
        ) : null}

        {/* Evidence Items */}
        {message.evidence?.length ? (
          <div className="evidence-section-v2">
            <details open>
              <summary className="evidence-toggle">
                <span className="ev-tag evidence">📋 证据溯源</span>
                <span className="evidence-count">{message.evidence.length} 条</span>
              </summary>
              <div className="evidence-list-v2">
                {message.evidence.map((ev, i) => (
                  <div className="evidence-item-v2" key={`ev-${message.id}-${i}`}>
                    <div className="evidence-source-bar">
                      <span className={`source-type-label ${ev.sourceType ?? ""}`}>{ev.sourceType ?? "?"}</span>
                      {ev.score != null ? (
                        <span className="evidence-score-bar">
                          <span className="score-fill" style={{ width: `${ev.score * 100}%` }}></span>
                          <span className="score-text">{(ev.score * 100).toFixed(0)}%</span>
                        </span>
                      ) : null}
                    </div>
                    {ev.title ? <div className="evidence-title-row">{ev.title}</div> : null}
                    {ev.reason ? <div className="evidence-reason-row">{ev.reason}</div> : null}
                  </div>
                ))}
              </div>
            </details>
          </div>
        ) : null}

        {/* Memory Hits */}
        {message.memoryHits?.length ? (
          <div className="memory-section-v2">
            <details>
              <summary className="memory-toggle">
                <span className="ev-tag memory">🧠 记忆命中</span>
                <span className="memory-count">{message.memoryHits.length} 条</span>
              </summary>
              <div className="memory-list-v2">
                {message.memoryHits.map((mh, i) => (
                  <div className="memory-item-v2" key={`mem-${message.id}-${i}`}>
                    <span className="memory-type-label">{mh.type ?? "short"}</span>
                    <span className="memory-content-text">{mh.content}</span>
                  </div>
                ))}
              </div>
            </details>
          </div>
        ) : null}

        {message.attachments?.length ? (
          <div className="attachment-list">
            {message.attachments.map((item) => (
              <span className="attachment-chip static" key={item.id}>
                {item.name} · {formatBytes(item.size)}
              </span>
            ))}
          </div>
        ) : null}

        {course ? (
          <article className="business-card course-card" data-testid="course-card">
            <div className="business-card-head course-gradient">
              <div className="card-head-left">
                <span className="card-type-badge">📚 课程</span>
                <strong>{course.name}</strong>
              </div>
              <div className="card-price-tag">{formatMoney(course.price)}</div>
            </div>
            <div className="business-card-grid">
              <div className="card-field">
                <span>适用人群</span>
                <strong>{course.usePeople}</strong>
              </div>
              <div className="card-field">
                <span>有效期</span>
                <strong>{course.validDuration ? `${course.validDuration} 月` : "长期"}</strong>
              </div>
              <div className="card-field">
                <span>课程 ID</span>
                <strong className="mono">{course.id}</strong>
              </div>
              <div className="card-field cta-field">
                <button className="card-cta-btn" type="button">了解详情 →</button>
              </div>
            </div>
            {course.detail ? <p className="card-detail">{course.detail}</p> : null}
          </article>
        ) : null}

        {order ? (
          <article className="business-card order-card" data-testid="order-card">
            <div className="business-card-head order-gradient">
              <div className="card-head-left">
                <span className="card-type-badge">🧾 订单</span>
                <strong>订单号 {order.orderId}</strong>
              </div>
              <div className="card-price-tag accent">{formatMoney(order.payAmount)}</div>
            </div>
            <div className="business-card-grid">
              <div className="card-field">
                <span>课程数</span>
                <strong>{order.count}</strong>
              </div>
              <div className="card-field">
                <span>原价</span>
                <strong>{formatMoney(order.totalAmount)}</strong>
              </div>
              <div className="card-field">
                <span>优惠</span>
                <strong className="discount">-{formatMoney(order.discountAmount)}</strong>
              </div>
              <div className="card-field">
                <span>实付</span>
                <strong className="pay-amount">{formatMoney(order.payAmount)}</strong>
              </div>
            </div>
            {order.couponName ? <p className="card-detail">🎫 {order.couponName}</p> : null}
          </article>
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
          <button onClick={() => onCopy(message.content)} type="button">
            复制
          </button>
          {message.role === "assistant" ? (
            <button onClick={toggleSpeech} type="button">
              {speaking ? "停止朗读" : "朗读"}
            </button>
          ) : null}
          {message.role === "assistant" ? (
            <button
              disabled={isStreaming || !message.originQuestion}
              onClick={() => onRetry(message)}
              type="button"
            >
              {message.status === "failed" ? "重试" : "重新生成"}
            </button>
          ) : null}
        </div>
      </div>
    </article>
  );
}
