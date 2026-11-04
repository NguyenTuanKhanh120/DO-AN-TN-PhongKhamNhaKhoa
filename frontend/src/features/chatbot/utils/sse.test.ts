import { describe, expect, it, vi } from "vitest";
import { consumeSseStream, parseSseBlock } from "./sse";

describe("SSE parser", () => {
  it("đọc event và ghép nhiều dòng data", () => {
    expect(parseSseBlock("event: token\ndata: {\"content\":\"xin\"}\ndata: chao")).toEqual({
      event: "token",
      data: "{\"content\":\"xin\"}\nchao",
    });
  });

  it("xử lý event bị chia qua nhiều network chunk", async () => {
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode("event: token\r\ndata: {\"content\":\"Xin"));
        controller.enqueue(encoder.encode(" chào\"}\r\n\r\nevent: completed\n"));
        controller.enqueue(encoder.encode("data: {\"messageId\":\"1\",\"createdAt\":\"now\"}\n\n"));
        controller.close();
      },
    });
    const onEvent = vi.fn();

    await consumeSseStream(stream, onEvent);

    expect(onEvent).toHaveBeenNthCalledWith(1, {
      event: "token",
      data: "{\"content\":\"Xin chào\"}",
    });
    expect(onEvent).toHaveBeenNthCalledWith(2, {
      event: "completed",
      data: "{\"messageId\":\"1\",\"createdAt\":\"now\"}",
    });
  });
});
