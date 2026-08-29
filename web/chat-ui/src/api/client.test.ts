import { afterEach, describe, expect, it, vi } from "vitest";
import { isValidChatEvent, streamChatEvents } from "./client";

function sseResponse(events: unknown[]) {
  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    start(controller) {
      events.forEach((event) => controller.enqueue(encoder.encode(`data:${JSON.stringify(event)}\n\n`)));
      controller.close();
    },
  });
  return new Response(stream, { status: 200, headers: { "Content-Type": "text/event-stream" } });
}

describe("SSE client contract", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("accepts a valid ROUTE -> DATA -> STOP sequence", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sseResponse([
      { eventType: 1004, eventData: { nextAgent: "RECOMMEND" } },
      { eventType: 1001, eventData: "课程推荐" },
      { eventType: 1002 },
    ])));
    const events: number[] = [];

    await streamChatEvents({ question: "推荐课程", sessionId: "s-1" }, "", undefined,
      (event) => events.push(event.eventType ?? 0));

    expect(events).toEqual([1004, 1001, 1002]);
  });

  it("drops invalid payloads and fails a stream that never sends STOP", async () => {
    expect(isValidChatEvent({ eventType: 1003, eventData: "not-a-card" })).toBe(false);
    expect(isValidChatEvent({ eventType: 1001, eventData: "" })).toBe(false);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sseResponse([
      { eventType: 1001, eventData: "incomplete" },
    ])));

    await expect(streamChatEvents({ question: "推荐课程", sessionId: "s-1" }, "", undefined, vi.fn()))
      .rejects.toThrow("STOP");
  });
});

  it("survives malformed frames: partial JSON, CRLF, unknown types, garbage lines", async () => {
    const encoder = new TextEncoder();
    const partialPrefix = `data:${JSON.stringify({ eventType: 1004, eventData: { nextAgent: "RECOMMEND" } }).slice(0, 32)}`;
    const rest = `${JSON.stringify({ eventType: 1004, eventData: { nextAgent: "RECOMMEND" } }).slice(32)}\n\n`;
    const raw = [
      `data:${JSON.stringify({ eventType: 1001, eventData: "你好" })}\r\n\r\n`, // CRLF framing
      partialPrefix,                 // a valid JSON prefix, completed in the next chunk
      rest,
      "data:{\"eventType\":9999,\"eventData\":{}}\n\n", // unknown event type -> dropped
      "data:not-json\n\n",                                  // garbage -> dropped
      "data:{\"eventType\":1002}\n\n",                    // STOP
    ].join("");
    const stream = new ReadableStream({
      start(controller) {
        // deliver the buffer in two chunks to simulate network fragmentation
        controller.enqueue(encoder.encode(raw.slice(0, raw.indexOf(rest))));
        controller.enqueue(encoder.encode(raw.slice(raw.indexOf(rest))));
        controller.close();
      },
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(stream, { status: 200, headers: { "Content-Type": "text/event-stream" } })));

    const events: number[] = [];
    await streamChatEvents({ question: "推荐课程", sessionId: "s-1" }, "", undefined,
      (event) => events.push(event.eventType ?? 0));

    expect(events).toEqual([1001, 1004, 1002]);
  });

  it("ignores events after STOP and never renders a duplicate STOP", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sseResponse([
      { eventType: 1004, eventData: { nextAgent: "RECOMMEND" } },
      { eventType: 1002 },
      { eventType: 1001, eventData: "late-data-after-stop" },
      { eventType: 1002 },
    ])));
    const events: number[] = [];
    await streamChatEvents({ question: "推荐课程", sessionId: "s-1" }, "", undefined,
      (event) => events.push(event.eventType ?? 0));

    expect(events).toEqual([1004, 1002]);
  });
