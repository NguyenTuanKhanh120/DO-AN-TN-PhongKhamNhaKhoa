import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { tokenStore } from "@/services/api/token-store";
import { onSessionEvent } from "@/services/api/session-events";
import { streamChatMessage } from "./chatbot-service";

const authMocks = vi.hoisted(() => ({ refreshAccessToken: vi.fn() }));
vi.mock("@/services/api/client", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/services/api/client")>(),
  refreshAccessToken: authMocks.refreshAccessToken,
}));

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
  beforeEach(() => {
    authMocks.refreshAccessToken.mockReset();
  });
  afterEach(() => {
    tokenStore.clear();
    vi.useRealTimers();
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

  it("dừng chờ phản hồi ban đầu và trả thông báo hệ thống sau 30 giây", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", vi.fn((_url: string, options?: RequestInit) => (
      new Promise<Response>((_resolve, reject) => {
        options?.signal?.addEventListener("abort", () => reject(options.signal?.reason));
      })
    )));

    const request = streamChatMessage(
      "Ngày mai còn lịch không?",
      {},
      new AbortController().signal,
    );
    const rejection = expect(request).rejects.toMatchObject({
      message: "hệ thống lỗi vui lòng thử lại sau",
      code: "NETWORK_ERROR",
    });

    await vi.advanceTimersByTimeAsync(30_000);
    await rejection;
  });

  it("không tạo request khi tín hiệu đã bị hủy", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();
    controller.abort();

    await expect(streamChatMessage("Xin chào", {}, controller.signal)).rejects.toMatchObject({ name: "AbortError" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("hủy fetch khi chưa nhận header", async () => {
    let requestSignal: AbortSignal | null | undefined;
    vi.stubGlobal("fetch", vi.fn((_url: string, options?: RequestInit) => {
      requestSignal = options?.signal;
      return new Promise<Response>((_resolve, reject) => {
        requestSignal?.addEventListener("abort", () => reject(requestSignal?.reason));
      });
    }));
    const controller = new AbortController();
    const request = streamChatMessage("Xin chào", {}, controller.signal);
    const rejection = expect(request).rejects.toMatchObject({ name: "AbortError" });

    controller.abort();

    await rejection;
    expect(requestSignal?.aborted).toBe(true);
  });

  it("giữ liên kết hủy sau header và dừng reader đang chờ dữ liệu", async () => {
    const cancel = vi.fn();
    const body = new ReadableStream<Uint8Array>({ cancel });
    let requestSignal: AbortSignal | null | undefined;
    vi.stubGlobal("fetch", vi.fn((_url: string, options?: RequestInit) => {
      requestSignal = options?.signal;
      return Promise.resolve(new Response(body));
    }));
    const controller = new AbortController();
    const onToken = vi.fn();
    const onCompleted = vi.fn();
    const request = streamChatMessage("Xin chào", { onToken, onCompleted }, controller.signal);
    const rejection = expect(request).rejects.toMatchObject({ name: "AbortError" });
    await vi.waitFor(() => expect(body.locked).toBe(true));

    controller.abort();

    await rejection;
    expect(requestSignal?.aborted).toBe(true);
    expect(cancel).toHaveBeenCalledOnce();
    expect(body.locked).toBe(false);
    expect(onToken).not.toHaveBeenCalled();
    expect(onCompleted).not.toHaveBeenCalled();
  });

  it("không phát metadata hoặc completed đã nằm trong buffer sau khi hủy giữa các token", async () => {
    const controller = new AbortController();
    const onToken = vi.fn(() => controller.abort());
    const onMetadata = vi.fn();
    const onCompleted = vi.fn();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(streamResponse([
      'event: token\ndata: {"content":"Một phần câu trả lời"}\n\n',
      'event: metadata\ndata: {"intents":[],"sources":[],"suggestedServices":[],"suggestedDoctors":[],"suggestedSlots":[],"needsClarification":false}\n\n',
      'event: completed\ndata: {"messageId":"assistant-1","createdAt":"2026-10-30T01:00:01Z"}\n\n',
    ].join(""))));

    await expect(streamChatMessage("Xin chào", { onToken, onMetadata, onCompleted }, controller.signal))
      .rejects.toMatchObject({ name: "AbortError" });
    expect(onToken).toHaveBeenCalledOnce();
    expect(onMetadata).not.toHaveBeenCalled();
    expect(onCompleted).not.toHaveBeenCalled();
  });

  it.each(["resolve", "reject"] as const)("hủy trong khi refresh và bỏ qua kết quả refresh đến muộn: %s", async (outcome) => {
    let finishRefresh!: () => void;
    let failRefresh!: (error: Error) => void;
    authMocks.refreshAccessToken.mockReturnValue(new Promise<void>((resolve, reject) => {
      finishRefresh = resolve;
      failRefresh = reject;
    }));
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 401 }));
    vi.stubGlobal("fetch", fetchMock);
    tokenStore.set("existing-token");
    const unauthorized = vi.fn();
    const removeListener = onSessionEvent("unauthorized", unauthorized);
    const controller = new AbortController();
    try {
      const request = streamChatMessage("Xin chào", {}, controller.signal);
      const rejection = expect(request).rejects.toMatchObject({ name: "AbortError" });
      await vi.waitFor(() => expect(authMocks.refreshAccessToken).toHaveBeenCalledOnce());
      controller.abort();
      await rejection;
      if (outcome === "resolve") finishRefresh();
      else failRefresh(new Error("late refresh error"));
      await Promise.resolve();

      expect(fetchMock).toHaveBeenCalledOnce();
      expect(unauthorized).not.toHaveBeenCalled();
      expect(tokenStore.get()).toBe("existing-token");
    } finally {
      removeListener();
    }
  });

  it("refresh thành công chỉ thử lại một lần và vẫn nhận đủ phản hồi", async () => {
    authMocks.refreshAccessToken.mockResolvedValue(undefined);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(streamResponse('event: completed\ndata: {"messageId":"assistant-1","createdAt":"2026-10-30T01:00:01Z"}\n\n'));
    vi.stubGlobal("fetch", fetchMock);
    const onCompleted = vi.fn();

    await streamChatMessage("Xin chào", { onCompleted }, new AbortController().signal);

    expect(authMocks.refreshAccessToken).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(onCompleted).toHaveBeenCalledOnce();
  });

  it("bỏ listener và timer sau khi hoàn tất", async () => {
    vi.useFakeTimers();
    const controller = new AbortController();
    const add = vi.spyOn(controller.signal, "addEventListener");
    const remove = vi.spyOn(controller.signal, "removeEventListener");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(streamResponse(
      'event: completed\ndata: {"messageId":"assistant-1","createdAt":"2026-10-30T01:00:01Z"}\n\n',
    )));

    await streamChatMessage("Xin chào", {}, controller.signal);

    expect(remove).toHaveBeenCalledWith("abort", add.mock.calls[0]?.[1]);
    expect(vi.getTimerCount()).toBe(0);
  });

  it("lỗi phân tích SSE hủy body và giải phóng reader", async () => {
    const cancel = vi.fn();
    const body = new ReadableStream<Uint8Array>({
      start(controller) { controller.enqueue(new TextEncoder().encode("event: token\ndata: invalid json\n\n")); },
      cancel,
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(body)));

    await expect(streamChatMessage("Xin chào", {}, new AbortController().signal))
      .rejects.toMatchObject({ message: "Phản hồi streaming từ máy chủ không hợp lệ" });
    expect(cancel).toHaveBeenCalledOnce();
    expect(body.locked).toBe(false);
  });
});
