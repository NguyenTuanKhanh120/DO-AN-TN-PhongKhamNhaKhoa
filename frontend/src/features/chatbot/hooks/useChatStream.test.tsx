import { act, cleanup, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { PropsWithChildren } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ChatStreamHandlers } from "../types/chatbot";
import { streamChatMessage } from "../services/chatbot-service";
import { useChatStream } from "./useChatStream";

vi.mock("../services/chatbot-service", () => ({ streamChatMessage: vi.fn() }));

function pendingRequest() {
  let resolve!: () => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<void>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

function renderStream() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(client, "invalidateQueries");
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return { ...renderHook(() => useChatStream(), { wrapper }), invalidate };
}

describe("useChatStream cancellation", () => {
  beforeEach(() => { vi.mocked(streamChatMessage).mockReset(); });
  afterEach(cleanup);

  it("hủy request, bỏ phần đang nhận và không biến hủy thành lỗi", async () => {
    const pending = pendingRequest();
    vi.mocked(streamChatMessage).mockImplementation((_message, _handlers, signal) => {
      signal.addEventListener("abort", () => pending.reject(signal.reason), { once: true });
      return pending.promise;
    });
    const { result, invalidate } = renderStream();
    let sent!: Promise<void>;
    act(() => { sent = result.current.sendMessage("Câu hỏi cũ"); });
    expect(result.current.isStreaming).toBe(true);
    const signal = vi.mocked(streamChatMessage).mock.calls[0]![2];

    await act(async () => { result.current.cancel(); await sent; });
    expect(signal.aborted).toBe(true);
    expect(result.current.turn).toBeNull();
    expect(result.current.error).toBeNull();
    expect(result.current.isStreaming).toBe(false);
    expect(invalidate).not.toHaveBeenCalled();
  });

  it.each(["resolve", "reject"] as const)("bỏ callback và kết quả muộn của request cũ: %s", async (outcome) => {
    const old = pendingRequest();
    const next = pendingRequest();
    let oldHandlers!: ChatStreamHandlers;
    let nextHandlers!: ChatStreamHandlers;
    vi.mocked(streamChatMessage)
      .mockImplementationOnce((_message, handlers) => { oldHandlers = handlers; return old.promise; })
      .mockImplementationOnce((_message, handlers) => { nextHandlers = handlers; return next.promise; });
    const { result, invalidate } = renderStream();
    let oldSent!: Promise<void>;
    let nextSent!: Promise<void>;
    act(() => { oldSent = result.current.sendMessage("Câu hỏi cũ"); });
    act(() => { result.current.cancel(); });
    act(() => { nextSent = result.current.sendMessage("Câu hỏi mới"); });
    act(() => {
      nextHandlers.onToken?.({ content: "Câu trả lời mới" });
      oldHandlers.onToken?.({ content: "Nội dung cũ đến muộn" });
      oldHandlers.onCompleted?.({ messageId: "old-id", createdAt: "2026-11-26T01:00:00Z" });
    });
    await act(async () => {
      if (outcome === "resolve") old.resolve();
      else old.reject(new Error("old request failed"));
      await oldSent;
    });
    expect(result.current.turn?.userMessage.content).toBe("Câu hỏi mới");
    expect(result.current.turn?.assistantMessage.content).toBe("Câu trả lời mới");
    expect(result.current.error).toBeNull();
    expect(invalidate).not.toHaveBeenCalled();
    expect(result.current.isStreaming).toBe(true);
    const nextSignal = vi.mocked(streamChatMessage).mock.calls[1]![2];
    act(() => { result.current.cancel(); });
    expect(nextSignal.aborted).toBe(true);
    await act(async () => { next.resolve(); await nextSent; });
  });

  it("unmount hủy request và bỏ callback đến muộn", async () => {
    const pending = pendingRequest();
    let handlers!: ChatStreamHandlers;
    vi.mocked(streamChatMessage).mockImplementation((_message, callbacks) => {
      handlers = callbacks;
      return pending.promise;
    });
    const { result, unmount, invalidate } = renderStream();
    let sent!: Promise<void>;
    act(() => { sent = result.current.sendMessage("Tôi muốn hỏi"); });
    const signal = vi.mocked(streamChatMessage).mock.calls[0]![2];
    unmount();
    expect(signal.aborted).toBe(true);
    handlers.onToken?.({ content: "Đến muộn" });
    pending.resolve();
    await sent;
    expect(invalidate).not.toHaveBeenCalled();
  });

  it("hoàn tất bình thường tải lại lịch sử và giải phóng trạng thái gửi", async () => {
    vi.mocked(streamChatMessage).mockImplementation(async (_message, handlers) => {
      handlers.onToken?.({ content: "Trả lời đầy đủ" });
      handlers.onCompleted?.({ messageId: "saved-id", createdAt: "2026-11-26T01:00:00Z" });
    });
    const { result, invalidate } = renderStream();
    await act(async () => { await result.current.sendMessage("Câu hỏi"); });
    expect(invalidate).toHaveBeenCalledOnce();
    expect(result.current.turn).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it("lỗi thật vẫn được hiển thị và có thể gửi lại", async () => {
    const failure = new Error("hệ thống lỗi vui lòng thử lại sau");
    vi.mocked(streamChatMessage).mockRejectedValueOnce(failure).mockResolvedValueOnce(undefined);
    const { result, invalidate } = renderStream();
    await act(async () => { await expect(result.current.sendMessage("Câu hỏi")).rejects.toBe(failure); });
    expect(result.current.error).toBe(failure);
    expect(result.current.isStreaming).toBe(false);
    await act(async () => { await result.current.sendMessage("Gửi lại"); });
    expect(result.current.error).toBeNull();
    expect(invalidate).toHaveBeenCalledTimes(2);
  });
});
