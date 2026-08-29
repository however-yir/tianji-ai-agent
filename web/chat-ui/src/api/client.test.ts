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
