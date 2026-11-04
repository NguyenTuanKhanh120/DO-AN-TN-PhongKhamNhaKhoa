import type { ApiResponse, PageResponse } from "@/types/api";
import type { ZodType } from "zod";
import { apiBaseUrl, apiClient, getCsrfHeaders, refreshAccessToken, unwrap } from "@/services/api/client";
import { ApiError } from "@/services/api/errors";
import { emitSessionEvent } from "@/services/api/session-events";
import { tokenStore } from "@/services/api/token-store";
import type {
  ChatMessage,
  ChatSession,
  ChatStreamHandlers,
  RawChatMessage,
} from "../types/chatbot";
import { consumeSseStream, type ParsedSseEvent } from "../utils/sse";
import {
  chatMetadataSchema,
  normalizeChatMetadata,
  streamCompletedSchema,
  streamErrorSchema,
  streamStartedSchema,
  streamTokenSchema,
} from "../utils/validation";

interface ErrorPayload {
  code?: string;
  message?: string;
}

export class ChatStreamError extends ApiError {
  readonly partial: boolean;

  constructor(message: string, code = "AI_SERVICE_UNAVAILABLE", status?: number, partial = false) {
    super(message, code, status);
    this.name = "ChatStreamError";
    this.partial = partial;
  }
}

export async function getChatSession(): Promise<ChatSession> {
  const { data } = await apiClient.get<ApiResponse<ChatSession>>("/api/chat/session");
  return unwrap(data);
}

export async function getChatMessages(page: number, size: number): Promise<PageResponse<ChatMessage>> {
  const { data } = await apiClient.get<ApiResponse<PageResponse<RawChatMessage>>>(
    "/api/chat/messages",
    { params: { page, size } },
  );
  const response = unwrap(data);
  return {
    ...response,
    content: response.content.map((message) => ({
      ...message,
      metadata: normalizeChatMetadata(message.metadata),
    })),
  };
}

export async function clearChatHistory(): Promise<void> {
  await apiClient.delete<ApiResponse<null>>("/api/chat/messages");
}

async function readError(response: Response): Promise<ChatStreamError> {
  let payload: ErrorPayload = {};
  try {
    const value: unknown = await response.json();
    if (typeof value === "object" && value !== null) {
      const record = value as Record<string, unknown>;
      payload = {
        code: typeof record.code === "string" ? record.code : undefined,
        message: typeof record.message === "string" ? record.message : undefined,
      };
    }
  } catch {
    payload = {};
  }
  return new ChatStreamError(
    payload.message ?? "Không thể kết nối với trợ lý AI. Vui lòng thử lại.",
    payload.code ?? "AI_SERVICE_UNAVAILABLE",
    response.status,
  );
}

async function openStream(
  message: string,
  signal: AbortSignal,
  retried = false,
): Promise<ReadableStream<Uint8Array>> {
  const token = tokenStore.get();
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl.replace(/\/+$/, "")}/api/chat/messages/stream`, {
      method: "POST",
      credentials: "include",
      signal,
      headers: {
        Accept: "text/event-stream",
        "Content-Type": "application/json",
        ...getCsrfHeaders(),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ message }),
    });
  } catch (error) {
    if (signal.aborted) throw error;
    throw new ChatStreamError(
      "Không thể kết nối đến trợ lý AI. Vui lòng kiểm tra kết nối và thử lại.",
      "NETWORK_ERROR",
    );
  }

  if (response.status === 423) {
    emitSessionEvent("locked");
    throw await readError(response);
  }

  if (response.status === 401 && !retried) {
    try {
      await refreshAccessToken();
    } catch (error) {
      tokenStore.clear();
      emitSessionEvent("unauthorized");
      if (error instanceof Error) throw error;
      throw new ChatStreamError("Phiên đăng nhập đã hết hạn", "AUTHENTICATION_REQUIRED", 401);
    }
    return openStream(message, signal, true);
  }

  if (!response.ok) throw await readError(response);
  if (!response.body) {
    throw new ChatStreamError("Trình duyệt không nhận được luồng phản hồi từ trợ lý AI");
  }
  return response.body;
}

function dispatchEvent(event: ParsedSseEvent, handlers: ChatStreamHandlers): boolean {
  let payload: unknown;
  try {
    payload = JSON.parse(event.data);
  } catch {
    throw new ChatStreamError("Phản hồi streaming từ máy chủ không hợp lệ");
  }

  if (event.event === "started") {
    handlers.onStarted?.(parsePayload(streamStartedSchema, payload));
    return false;
  }
  if (event.event === "token") {
    handlers.onToken?.(parsePayload(streamTokenSchema, payload));
    return false;
  }
  if (event.event === "metadata") {
    handlers.onMetadata?.(parsePayload(chatMetadataSchema, payload));
    return false;
  }
  if (event.event === "completed") {
    handlers.onCompleted?.(parsePayload(streamCompletedSchema, payload));
    return true;
  }
  if (event.event === "error") {
    const error = parsePayload(streamErrorSchema, payload);
    throw new ChatStreamError(error.message, error.code, undefined, error.partial);
  }
  return false;
}

function parsePayload<T>(schema: ZodType<T>, payload: unknown): T {
  const parsed = schema.safeParse(payload);
  if (!parsed.success) {
    throw new ChatStreamError("Phản hồi streaming từ máy chủ không đúng định dạng");
  }
  return parsed.data;
}

export async function streamChatMessage(
  message: string,
  handlers: ChatStreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  const stream = await openStream(message, signal);
  let completed = false;
  await consumeSseStream(stream, (event) => {
    completed = dispatchEvent(event, handlers) || completed;
  });
  if (!completed && !signal.aborted) {
    throw new ChatStreamError("Luồng phản hồi kết thúc trước khi hoàn tất");
  }
}
