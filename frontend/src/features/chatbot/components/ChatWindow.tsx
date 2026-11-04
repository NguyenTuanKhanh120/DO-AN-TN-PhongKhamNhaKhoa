import { AlertTriangle, Bot, ChevronUp, RotateCcw, Trash2, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { useToast } from "@/contexts/ToastContext";
import { getApiErrorMessage } from "@/services/api/errors";
import { cn } from "@/utils/cn";
import { useChat } from "../hooks/useChat";
import { useChatStream } from "../hooks/useChatStream";
import { ChatInput } from "./ChatInput";
import { ChatMessage } from "./ChatMessage";

export function ChatWindow({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { showToast } = useToast();
  const { session, history, messages, clearHistory } = useChat();
  const stream = useChatStream();
  const [confirmClear, setConfirmClear] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);
  const displayedMessages = useMemo(() => {
    if (!stream.turn) return messages;
    const optimisticIds = new Set([
      stream.turn.userMessage.id,
      stream.turn.assistantMessage.id,
    ]);
    return [
      ...messages.filter((message) => !optimisticIds.has(message.id)),
      stream.turn.userMessage,
      stream.turn.assistantMessage,
    ];
  }, [messages, stream.turn]);
  const latestMessage = displayedMessages[displayedMessages.length - 1];

  useEffect(() => {
    if (open) endRef.current?.scrollIntoView({ block: "end" });
  }, [open, latestMessage?.id, latestMessage?.content]);

  const send = async (message: string): Promise<boolean> => {
    try {
      await stream.sendMessage(message);
      return true;
    } catch (error) {
      showToast(getApiErrorMessage(error), "error");
      return false;
    }
  };

  const clear = async () => {
    try {
      await clearHistory.mutateAsync();
      setConfirmClear(false);
      stream.dismissError();
      showToast("Đã xóa lịch sử trò chuyện", "success");
    } catch (error) {
      showToast(getApiErrorMessage(error), "error");
    }
  };

  const error = session.error ?? history.error;
  return (
    <>
      <section
        role="dialog"
        aria-modal="false"
        aria-label="Trợ lý nha khoa An Tâm"
        aria-hidden={!open}
        inert={!open}
        className={cn(
          "fixed z-50 flex overflow-hidden bg-slate-50 shadow-2xl transition duration-200",
          "inset-0 sm:inset-auto sm:bottom-24 sm:right-6 sm:h-[min(720px,calc(100vh-8rem))] sm:w-[420px] sm:rounded-3xl sm:border sm:border-slate-200",
          open ? "translate-y-0 opacity-100" : "pointer-events-none translate-y-4 opacity-0",
        )}
      >
        <div className="flex min-h-0 w-full flex-col">
          <header className="flex items-center gap-3 bg-gradient-to-r from-primary-800 to-cyanbrand px-4 py-3.5 text-white">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-white/15"><Bot className="h-5 w-5" /></span>
            <div className="min-w-0 flex-1">
              <h2 className="font-bold">Trợ lý nha khoa An Tâm</h2>
              <p className="text-xs text-white/75">Tư vấn định hướng, bác sĩ và lịch trống</p>
            </div>
            <button
              type="button"
              onClick={() => setConfirmClear(true)}
              disabled={stream.isStreaming || displayedMessages.length === 0}
              className="rounded-lg p-2 text-white/80 hover:bg-white/10 disabled:opacity-40"
              aria-label="Xóa lịch sử trò chuyện"
            >
              <Trash2 className="h-4 w-4" />
            </button>
            <button type="button" onClick={onClose} className="rounded-lg p-2 text-white/80 hover:bg-white/10" aria-label="Đóng chatbot">
              <X className="h-5 w-5" />
            </button>
          </header>

          <div className="border-b border-amber-100 bg-amber-50 px-4 py-2 text-[11px] leading-4 text-amber-800">
            Chatbot cung cấp thông tin tham khảo, không thay thế chẩn đoán hoặc điều trị của bác sĩ.
          </div>

          {stream.error !== null && (
            <div className="flex items-start gap-2 border-b border-red-100 bg-red-50 px-3 py-2 text-xs text-red-700">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <p className="flex-1">{getApiErrorMessage(stream.error)}</p>
              <button type="button" onClick={stream.dismissError} aria-label="Đóng thông báo lỗi"><X className="h-4 w-4" /></button>
            </div>
          )}

          <div className="min-h-0 flex-1 overflow-y-auto p-3 sm:p-4">
            <AsyncStateView
              loading={session.isLoading || history.isLoading}
              error={error}
              empty={displayedMessages.length === 0}
              emptyTitle="Bắt đầu trò chuyện với trợ lý nha khoa"
              emptyDescription="Bạn có thể mô tả triệu chứng, hỏi bác sĩ phù hợp hoặc tìm lịch trống mà không cần theo thứ tự."
              onRetry={() => {
                void Promise.all([session.refetch(), history.refetch()]);
              }}
            >
              <div className="space-y-4">
                {history.hasNextPage && (
                  <div className="flex justify-center">
                    <Button
                      size="sm"
                      variant="ghost"
                      loading={history.isFetchingNextPage}
                      onClick={() => void history.fetchNextPage()}
                    >
                      <ChevronUp className="h-4 w-4" /> Xem tin nhắn cũ hơn
                    </Button>
                  </div>
                )}
                {displayedMessages.map((message) => (
                  <ChatMessage
                    key={message.id}
                    message={message}
                    streaming={stream.isStreaming && message.id === stream.turn?.assistantMessage.id}
                  />
                ))}
                <div ref={endRef} />
              </div>
            </AsyncStateView>
          </div>

          <ChatInput onSend={send} disabled={stream.isStreaming || Boolean(error)} />
          {error && (
            <div className="border-t border-slate-200 bg-white p-2 text-center">
              <Button size="sm" variant="ghost" onClick={() => void Promise.all([session.refetch(), history.refetch()])}>
                <RotateCcw className="h-4 w-4" /> Thử tải lại
              </Button>
            </div>
          )}
        </div>
      </section>
      <ConfirmDialog
        open={confirmClear}
        onClose={() => setConfirmClear(false)}
        onConfirm={() => void clear()}
        title="Xóa lịch sử trò chuyện?"
        description="Toàn bộ tin nhắn sẽ bị xóa và ngữ cảnh chatbot được đặt lại. Hành động này không ảnh hưởng đến lịch khám hoặc bệnh án của bạn."
        confirmText="Xóa lịch sử"
        danger
        loading={clearHistory.isPending}
      />
    </>
  );
}
