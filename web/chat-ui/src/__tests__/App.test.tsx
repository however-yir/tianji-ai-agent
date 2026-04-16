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
  });

  it("shows a friendly 401 banner in api mode", async () => {
    window.localStorage.setItem("tianji.chat.run-mode", JSON.stringify("api"));
    window.localStorage.setItem("tianji.chat.api-token", JSON.stringify("Bearer demo-token"));
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
    window.localStorage.setItem("tianji.chat.api-token", JSON.stringify("Bearer demo-token"));

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

    await waitFor(() => {
      expect(screen.getByTestId("rich-markdown")).toHaveTextContent("这是来自附件的回答。");
    });
    expect(await screen.findByText("brief.txt")).toBeInTheDocument();
    expect(screen.getByText(/引用与说明/)).toBeInTheDocument();
  });

  it("supports retry after api streaming failure", async () => {
    window.localStorage.setItem("tianji.chat.run-mode", JSON.stringify("api"));
    window.localStorage.setItem("tianji.chat.api-token", JSON.stringify("Bearer demo-token"));

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

    expect(await screen.findByText(/服务端出现异常/)).toBeInTheDocument();

    await user.click(await screen.findByRole("button", { name: "重试" }));

    await waitFor(() => {
      expect(screen.getByTestId("rich-markdown")).toHaveTextContent("重试成功。");
    });
    expect(screen.getByRole("button", { name: "重新生成" })).toBeInTheDocument();
  });
});
