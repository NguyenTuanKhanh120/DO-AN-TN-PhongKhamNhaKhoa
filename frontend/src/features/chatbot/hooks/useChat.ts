import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ChatMessage } from "../types/chatbot";
import * as chatbotService from "../services/chatbot-service";

const HISTORY_PAGE_SIZE = 30;

export const chatbotKeys = {
  all: ["chatbot"] as const,
  session: ["chatbot", "session"] as const,
  messages: ["chatbot", "messages"] as const,
};

export function useChat(enabled = true) {
  const queryClient = useQueryClient();
  const session = useQuery({
    queryKey: chatbotKeys.session,
    queryFn: chatbotService.getChatSession,
    enabled,
    staleTime: Number.POSITIVE_INFINITY,
  });
  const history = useInfiniteQuery({
    queryKey: chatbotKeys.messages,
    queryFn: ({ pageParam }) => chatbotService.getChatMessages(pageParam, HISTORY_PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.page + 1 : undefined,
    enabled,
  });
  const clearHistory = useMutation({
    mutationFn: chatbotService.clearChatHistory,
    onSuccess: async () => {
      await queryClient.resetQueries({ queryKey: chatbotKeys.messages });
    },
  });

  const messages: ChatMessage[] = history.data?.pages
    .slice()
    .reverse()
    .flatMap((page) => page.content.slice().reverse()) ?? [];

  return {
    session,
    history,
    messages,
    clearHistory,
  };
}
