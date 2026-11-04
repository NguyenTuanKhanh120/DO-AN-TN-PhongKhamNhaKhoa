import { afterEach, describe, expect, it, vi } from "vitest";
import { tokenStore } from "@/services/api/token-store";
import { streamChatMessage } from "./chatbot-service";

function streamResponse(payload: string): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(encoder.encode(payload.slice(0, 80)));
      controller.enqueue(encoder.encode(payload.slice(80)));
      controller.close();
    },
  });
  return new Response(body, { status: 200, headers: { "Content-Type": "text/event-stream" } });
}

describe("chatbot streaming service", () => {
  afterEach(() => {
    tokenStore.clear();
    vi.unstubAllGlobals();
  });

  it("gửi Bearer token và phát đủ chuỗi sự kiện SSE", async () => {
    tokenStore.set("access-token");
    const payload = [
      "event: started\ndata: {\"userMessageId\":\"user-message\",\"startedAt\":\"2026-10-30T01:00:00Z\"}\n\n",
      "event: token\ndata: {\"content\":\"Xin chào\"}\n\n",
      "event: metadata\ndata: {\"intents\":[],\"sources\":[],\"suggestedServices\":[],\"suggestedDoctors\":[],\"suggestedSlots\":[],\"needsClarification\":false}\n\n",
      "event: completed\ndata: {\"messageId\":\"assistant-message\",\"createdAt\":\"2026-10-30T01:00:01Z\"}\n\n",
    ].join("");
    const fetchMock = vi.fn().mockResolvedValue(streamResponse(payload));
    vi.stubGlobal("fetch", fetchMock);
    const onStarted = vi.fn();
    const onToken = vi.fn();
    const onMetadata = vi.fn();
    const onCompleted = vi.fn();

    await streamChatMessage(
      "Tôi đau răng",
      { onStarted, onToken, onMetadata, onCompleted },
      new AbortController().signal,
    );

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls[0]?.[1]?.headers).toMatchObject({
      Accept: "text/event-stream",
      Authorization: "Bearer access-token",
    });
    expect(onStarted).toHaveBeenCalledWith(expect.objectContaining({ userMessageId: "user-message" }));
    expect(onToken).toHaveBeenCalledWith({ content: "Xin chào" });
    expect(onMetadata).toHaveBeenCalledWith(expect.objectContaining({ needsClarification: false }));
    expect(onCompleted).toHaveBeenCalledWith(expect.objectContaining({ messageId: "assistant-message" }));
  });
});
