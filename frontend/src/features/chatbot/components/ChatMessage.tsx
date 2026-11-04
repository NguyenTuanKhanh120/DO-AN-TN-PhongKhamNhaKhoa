import { Bot, ExternalLink, UserRound } from "lucide-react";
import type { ChatMessage as ChatMessageModel } from "../types/chatbot";
import { DoctorSuggestionCard } from "./DoctorSuggestionCard";
import { ServiceSuggestionCard } from "./ServiceSuggestionCard";
import { SlotSuggestionList } from "./SlotSuggestionList";
import { TypingIndicator } from "./TypingIndicator";
import { safeExternalUrl } from "../utils/navigation";

function messageTime(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function normalizeAssistantContent(value: string): string {
  return value
    .replace(/^\s*[*+]\s+/gm, "- ")
    .replace(/^\s*#{1,6}\s+/gm, "")
    .replaceAll("**", "")
    .replaceAll("__", "")
    .replaceAll("*", "")
    .replaceAll("`", "")
    .trim();
}

export function ChatMessage({ message, streaming = false }: {
  message: ChatMessageModel;
  streaming?: boolean;
}) {
  const isUser = message.sender === "USER";
  const displayContent = isUser ? message.content : normalizeAssistantContent(message.content);
  const metadata = message.metadata;
  const fallbackServiceId = metadata.suggestedServices[0]?.id
    ?? metadata.suggestedSlots.find((slot) => slot.serviceId)?.serviceId
    ?? undefined;

  if (message.sender === "SYSTEM") {
    return <p className="mx-auto max-w-sm rounded-lg bg-slate-100 px-3 py-2 text-center text-xs text-muted">{message.content}</p>;
  }

  return (
    <div className={`flex items-start gap-2 ${isUser ? "flex-row-reverse" : ""}`}>
      <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-xl ${isUser ? "bg-primary-700 text-white" : "bg-primary-50 text-primary-700"}`}>
        {isUser ? <UserRound className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
      </span>
      <div className={`flex min-w-0 max-w-[85%] flex-col ${isUser ? "items-end" : "items-start"}`}>
        <div className={`rounded-2xl px-3.5 py-2.5 text-sm leading-6 ${isUser ? "rounded-tr-sm bg-primary-700 text-white" : "rounded-tl-sm border border-slate-200 bg-white text-ink"}`}>
          {displayContent ? <p className="whitespace-pre-wrap break-words">{displayContent}</p> : streaming ? <TypingIndicator /> : null}
        </div>
        {!isUser && metadata.suggestedServices.length > 0 && (
          <div className="mt-2 w-full space-y-2">
            {metadata.suggestedServices.map((service) => <ServiceSuggestionCard key={service.id} service={service} />)}
          </div>
        )}
        {!isUser && metadata.suggestedDoctors.length > 0 && (
          <div className="mt-2 w-full space-y-2">
            {metadata.suggestedDoctors.map((doctor) => (
              <DoctorSuggestionCard key={doctor.id} doctor={doctor} serviceId={fallbackServiceId} />
            ))}
          </div>
        )}
        {!isUser && metadata.suggestedSlots.length > 0 && (
          <div className="mt-2 w-full"><SlotSuggestionList slots={metadata.suggestedSlots} fallbackServiceId={fallbackServiceId} /></div>
        )}
        {!isUser && metadata.sources.length > 0 && (
          <div className="mt-2 w-full rounded-xl border border-slate-200 bg-slate-50 p-3">
            <p className="text-[11px] font-bold uppercase tracking-wide text-slate-500">Nguồn tham khảo</p>
            <ul className="mt-1.5 space-y-1">
              {metadata.sources.map((source) => {
                const href = safeExternalUrl(source.url);
                return <li key={`${source.recordId}-${source.url}`} className="text-xs text-slate-600">{href ? <a href={href} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-semibold text-primary-700 hover:underline">{source.title}<ExternalLink className="h-3 w-3" /></a> : source.title}</li>;
              })}
            </ul>
          </div>
        )}
        <time className="mt-1 block px-1 text-[10px] text-slate-400" dateTime={message.createdAt}>{messageTime(message.createdAt)}</time>
      </div>
    </div>
  );
}
