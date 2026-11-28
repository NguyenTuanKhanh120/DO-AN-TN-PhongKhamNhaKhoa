import { useCallback, useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { ChatMessage, StreamingChatTurn } from "../types/chatbot";
import { emptyChatMetadata } from "../types/chatbot";
import { streamChatMessage } from "../services/chatbot-service";
import { chatbotKeys } from "./useChat";

function temporaryMessage(sender: ChatMessage["sender"], content: string): ChatMessage {
  return {
    id: crypto.randomUUID(),
    sender,
    content,
    metadata: emptyChatMetadata(),
    createdAt: new Date().toISOString(),
  };
}

export function useChatStream() {
  const queryClient = useQueryClient();
  const controllerRef = useRef<AbortController | null>(null);
  const [turn, setTurn] = useState<StreamingChatTurn | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => () => {
    controllerRef.current?.abort();
    controllerRef.current = null;
  }, []);

  const cancel = useCallback(() => {
    controllerRef.current?.abort();
    controllerRef.current = null;
    setTurn(null);
    setError(null);
  }, []);

  const sendMessage = useCallback(async (message: string): Promise<void> => {
    if (controllerRef.current) return;
    const controller = new AbortController();
    controllerRef.current = controller;
    const isCurrent = () => controllerRef.current === controller && !controller.signal.aborted;
    const updateTurn = (update: (current: StreamingChatTurn) => StreamingChatTurn) => {
      if (isCurrent()) setTurn((current) => isCurrent() && current ? update(current) : current);
    };
    setError(null);
    setTurn({
      userMessage: temporaryMessage("USER", message),
      assistantMessage: temporaryMessage("ASSISTANT", ""),
    });

    try {
      await streamChatMessage(message, {
        onStarted: ({ userMessageId, startedAt }) => updateTurn((current) => ({
          ...current,
          userMessage: { ...current.userMessage, id: userMessageId, createdAt: startedAt },
        })),
        onToken: ({ content }) => updateTurn((current) => ({
          ...current,
          assistantMessage: {
            ...current.assistantMessage,
            content: `${current.assistantMessage.content}${content}`,
          },
        })),
        onMetadata: (metadata) => updateTurn((current) => ({
          ...current,
          assistantMessage: { ...current.assistantMessage, metadata },
        })),
        onCompleted: ({ messageId, createdAt }) => updateTurn((current) => ({
          ...current,
          assistantMessage: { ...current.assistantMessage, id: messageId, createdAt },
        })),
      }, controller.signal);
      if (!isCurrent()) return;
      await queryClient.invalidateQueries({ queryKey: chatbotKeys.messages });
      if (isCurrent()) setTurn(null);
    } catch (streamError) {
      if (controller.signal.aborted) return;
      setError(streamError);
      await queryClient.invalidateQueries({ queryKey: chatbotKeys.messages });
      if (!isCurrent()) return;
      setTurn(null);
      throw streamError;
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null;
    }
  }, [queryClient]);

  const dismissError = useCallback(() => setError(null), []);

  return {
    turn,
    error,
    isStreaming: turn !== null,
    sendMessage,
    cancel,
    dismissError,
  };
}
