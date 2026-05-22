import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "../App";

vi.mock("../components/RichMarkdown", () => ({
  default: ({ content }: { content: string }) => <div data-testid="rich-markdown">{content}</div>,
}));

function jsonResponse(payload: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(payload), {
      status,
      headers: {
        "Content-Type": "application/json",
      },
    }),
  );
}

function sseResponse(events: unknown[]) {
  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    start(controller) {
      events.forEach((event) => {
        controller.enqueue(encoder.encode(`data:${JSON.stringify(event)}\n\n`));
      });
      controller.close();
    },
  });
  return Promise.resolve(
    new Response(stream, {
      status: 200,
      headers: {
        "Content-Type": "text/event-stream",
      },
    }),
  );
}

describe("App", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.stubGlobal("fetch", vi.fn());
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it("shows a friendly 401 banner in api mode", async () => {
    window.localStorage.setItem("tianji.chat.run-mode", JSON.stringify("api"));
    window.sessionStorage.setItem("tianji.chat.api-token", JSON.stringify({ value: "Bearer demo-token", expiresAt: Date.now() + 3600000 }));
    vi.mocked(fetch)
      .mockImplementationOnce(() =>
        Promise.resolve(
          new Response(JSON.stringify({ message: "unauthorized" }), {
            status: 401,
            headers: { "Content-Type": "application/json" },
          }),
        ),
      )
      .mockImplementationOnce(() =>
        Promise.resolve(
          new Response(JSON.stringify({ message: "unauthorized" }), {
            status: 401,
            headers: { "Content-Type": "application/json" },
          }),
        ),
      );

    render(<App />);

    expect(await screen.findByText(/接口返回 401/)).toBeInTheDocument();
    await waitFor(() => {
      expect(fetch).toHaveBeenCalledTimes(2);
    });
  });

  it("uploads attachments and renders streamed references in api mode", async () => {
    window.localStorage.setItem("tianji.chat.run-mode", JSON.stringify("api"));
    window.sessionStorage.setItem("tianji.chat.api-token", JSON.stringify({ value: "Bearer demo-token", expiresAt: Date.now() + 3600000 }));

    vi.mocked(fetch)
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() =>
        jsonResponse({ sessionId: "session-1", title: "新的对话", examples: [] }),
      )
      .mockImplementationOnce(() =>
        jsonResponse([
          {
            attachmentId: "att-1",
            name: "brief.txt",
            contentType: "text/plain",
            size: 18,
            previewText: "项目目标是完善附件问答能力。",
            chunkCount: 2,
          },
        ]),
      )
      .mockImplementationOnce(() =>
        sseResponse([
          { eventType: 1001, eventData: "这是来自附件的回答。" },
          {
            eventType: 1003,
            eventData: {
              attachmentIds: ["att-1"],
              attachmentCount: 1,
              sources: [
                {
                  attachmentId: "att-1",
                  attachmentName: "brief.txt",
                  chunkIndex: 1,
                  excerpt: "项目目标是完善附件问答能力。",
                },
              ],
            },
          },
          { eventType: 1002, eventData: "stop" },
        ]),
      );

    const user = userEvent.setup();
    const { container } = render(<App />);
    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;

    await user.upload(
      fileInput,
      new File(["项目目标是完善附件问答能力。"], "brief.txt", { type: "text/plain" }),
    );
    await user.type(screen.getByPlaceholderText(/连接真实 API 后即可联调/), "请总结附件重点");
    await user.click(screen.getByRole("button", { name: "发送" }));

    await waitFor(
      () => {
        expect(screen.getByTestId("rich-markdown")).toHaveTextContent("这是来自附件的回答。");
      },
      { timeout: 3000 },
    );
    expect(screen.getAllByText(/brief.txt/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/引用与说明/)).toBeInTheDocument();
  });

  it("supports retry after api streaming failure", async () => {
    window.localStorage.setItem("tianji.chat.run-mode", JSON.stringify("api"));
    window.sessionStorage.setItem("tianji.chat.api-token", JSON.stringify({ value: "Bearer demo-token", expiresAt: Date.now() + 3600000 }));

    vi.mocked(fetch)
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse({ sessionId: "session-2", title: "新的对话", examples: [] }))
      .mockImplementationOnce(() =>
        Promise.resolve(
          new Response(JSON.stringify({ message: "server error" }), {
            status: 500,
            headers: { "Content-Type": "application/json" },
          }),
        ),
      )
      .mockImplementationOnce(() =>
        sseResponse([
          { eventType: 1001, eventData: "重试成功。" },
          { eventType: 1002, eventData: "stop" },
        ]),
      );

    const user = userEvent.setup();
    render(<App />);

    await user.type(screen.getByPlaceholderText(/连接真实 API 后即可联调/), "帮我输出一份运行建议");
    await user.click(screen.getByRole("button", { name: "发送" }));

    await waitFor(
      () => {
        expect(screen.getByText(/服务端出现异常/)).toBeInTheDocument();
      },
      { timeout: 3000 },
    );

    await user.click(screen.getByRole("button", { name: "重试" }));

    await waitFor(
      () => {
        expect(screen.getByTestId("rich-markdown")).toHaveTextContent("重试成功。");
      },
      { timeout: 3000 },
    );
    expect(screen.getByRole("button", { name: "重新生成" })).toBeInTheDocument();
  });

  it("renders route trace inside streamed course and order cards in api mode", async () => {
    window.localStorage.setItem("tianji.chat.run-mode", JSON.stringify("api"));
    window.sessionStorage.setItem("tianji.chat.api-token", JSON.stringify({ value: "Bearer demo-token", expiresAt: Date.now() + 3600000 }));

    vi.mocked(fetch)
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse({ sessionId: "session-route-card", title: "新的对话", examples: [] }))
      .mockImplementationOnce(() =>
        sseResponse([
          {
            eventType: 1004,
            eventData: {
              intent: "BUY",
              confidence: 0.91,
              routeReason: "购买意图明确，展示订单预览",
              nextAgent: "BUY",
              candidateAgents: ["BUY", "CONSULT", "HUMAN_HANDOFF"],
              riskLevel: "LOW",
            },
          },
          { eventType: 1001, eventData: "已生成课程和订单预览。" },
          {
            eventType: 1003,
            eventData: {
              courseInfo_1589905661084430337: {
                id: "1589905661084430337",
                name: "Java 后端工程师体系课",
                price: 19900,
                validDuration: 12,
                usePeople: "零基础或转行学习者",
                detail: "覆盖 Java 基础、Spring Boot、Redis 和项目实战。",
              },
              prePlaceOrder: {
                count: 1,
                totalAmount: 19900,
                discountAmount: 2000,
                payAmount: 17900,
                orderId: "202604290001",
                couponName: "单券：【新人立减 20 元】",
                businessState: "USER_CONFIRM_REQUIRED",
                stateTrace: ["COURSE_SELECTED", "ORDER_PREVIEW", "USER_CONFIRM_REQUIRED"],
                nextAction: "等待用户确认订单；支付异常或敏感问题进入 HANDOFF_OR_DONE",
              },
            },
          },
          { eventType: 1002, eventData: "stop" },
        ]),
      );

    const user = userEvent.setup();
    render(<App />);

    await user.type(screen.getByPlaceholderText(/连接真实 API 后即可联调/), "我要购买课程 1589905661084430337，帮我生成确认订单");
    await user.click(screen.getByRole("button", { name: "发送" }));

    await waitFor(
      () => {
        expect(screen.getByTestId("course-card")).toBeInTheDocument();
        expect(screen.getByTestId("order-card")).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
    expect(screen.getByText("Java 后端工程师体系课")).toBeInTheDocument();
    expect(screen.getByText("USER_CONFIRM_REQUIRED")).toBeInTheDocument();
    expect(screen.getByText(/COURSE_SELECTED -> ORDER_PREVIEW -> USER_CONFIRM_REQUIRED/)).toBeInTheDocument();
    expect(screen.getAllByTestId("route-trace-panel").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText(/购买意图明确/).length).toBeGreaterThanOrEqual(1);
  });
});
