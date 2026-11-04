import { CalendarCheck2, Clock3 } from "lucide-react";
import { Link } from "react-router-dom";
import type { ChatSlotSuggestion } from "../types/chatbot";
import { buildBookingUrl } from "../utils/navigation";
import { formatDate, sessionLabels } from "@/utils/format";

function slotTime(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export function SlotSuggestionList({ slots, fallbackServiceId }: {
  slots: ChatSlotSuggestion[];
  fallbackServiceId?: string;
}) {
  return (
    <div className="space-y-2">
      {slots.map((slot) => {
        const serviceId = slot.serviceId ?? fallbackServiceId;
        return (
          <Link
            key={`${slot.doctorId}-${slot.startAt}`}
            to={buildBookingUrl({
              serviceId,
              doctorId: slot.doctorId,
              date: slot.date,
              scheduledStartAt: slot.startAt,
            })}
            className="flex items-center justify-between gap-3 rounded-xl border border-primary-100 bg-white p-3 transition hover:border-primary-300 hover:bg-primary-50"
          >
            <span className="min-w-0">
              <span className="flex items-center gap-1.5 text-xs font-bold text-ink">
                <CalendarCheck2 className="h-4 w-4 text-primary-700" /> {formatDate(slot.date)}
              </span>
              <span className="mt-1 block text-xs text-muted">{sessionLabels[slot.session]}</span>
            </span>
            <span className="flex shrink-0 items-center gap-1 text-sm font-bold text-primary-700">
              <Clock3 className="h-4 w-4" /> {slotTime(slot.startAt)}
            </span>
          </Link>
        );
      })}
    </div>
  );
}
