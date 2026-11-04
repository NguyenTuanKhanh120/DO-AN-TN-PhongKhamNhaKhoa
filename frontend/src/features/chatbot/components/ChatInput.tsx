import { zodResolver } from "@hookform/resolvers/zod";
import { SendHorizontal } from "lucide-react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/Button";
import { Textarea } from "@/components/ui/Textarea";
import { chatMessageSchema, type ChatMessageFormValues } from "../utils/validation";

export function ChatInput({ onSend, disabled = false }: {
  onSend: (message: string) => Promise<boolean>;
  disabled?: boolean;
}) {
  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ChatMessageFormValues>({
    resolver: zodResolver(chatMessageSchema),
    defaultValues: { message: "" },
  });
  const message = watch("message");
  const submit = handleSubmit(async (values) => {
    const submittedMessage = values.message;
    reset();
    const sent = await onSend(submittedMessage);
    if (!sent) reset({ message: submittedMessage });
  });
  const messageField = register("message");

  return (
    <form className="border-t border-slate-200 bg-white p-3" onSubmit={submit}>
      <div className="flex items-end gap-2">
        <div className="min-w-0 flex-1">
          <Textarea
            {...messageField}
            rows={1}
            className="max-h-32 min-h-11 resize-none py-2.5"
            placeholder="Mô tả triệu chứng hoặc hỏi lịch bác sĩ…"
            aria-label="Nội dung trò chuyện"
            error={Boolean(errors.message)}
            disabled={disabled || isSubmitting}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
                event.preventDefault();
                void submit();
              }
            }}
          />
        </div>
        <Button
          type="submit"
          size="sm"
          className="h-11 w-11 shrink-0 px-0"
          loading={isSubmitting}
          disabled={disabled}
          aria-label="Gửi tin nhắn"
        >
          <SendHorizontal className="h-4 w-4" />
        </Button>
      </div>
      <div className="mt-1 flex items-start justify-between gap-2 px-1">
        <p className="text-[11px] text-red-600">{errors.message?.message}</p>
        <p className={`ml-auto shrink-0 text-[10px] ${message.length > 1900 ? "text-amber-600" : "text-slate-400"}`}>{message.length}/2000</p>
      </div>
    </form>
  );
}
