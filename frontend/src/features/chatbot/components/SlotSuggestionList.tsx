import { CalendarCheck2, Clock3 } from "lucide-react";
import { Link } from "react-router-dom";
import type { ChatSlotSuggestion } from "../types/chatbot";
import { buildBookingUrl } from "../utils/navigation";
import { formatDate, formatQueueLabel, formatSessionWindow, formatTime } from "@/utils/format";

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
            key={`${slot.doctorId}-${slot.date}-${slot.session}-${slot.queueNumber}`}
            to={buildBookingUrl({
              serviceId,
              doctorId: slot.doctorId,
              date: slot.date,
              session: slot.session,
              queueNumber: slot.queueNumber,
            })}
            className="flex items-center justify-between gap-3 rounded-xl border border-primary-100 bg-white p-3 transition hover:border-primary-300 hover:bg-primary-50"
          >
            <span className="min-w-0">
              <span className="flex items-center gap-1.5 text-xs font-bold text-ink">
                <CalendarCheck2 className="h-4 w-4 text-primary-700" /> {formatDate(slot.date)}
              </span>
              <span className="mt-1 block text-xs text-muted">{formatSessionWindow(slot.session, slot.sessionStartTime, slot.sessionEndTime)} · {formatQueueLabel(slot.queueNumber)}</span>
            </span>
            <span className="flex shrink-0 items-center gap-1 text-sm font-bold text-primary-700">
              <Clock3 className="h-4 w-4" /> Dự kiến {formatTime(slot.estimatedStartTime)}
            </span>
          </Link>
        );
      })}
    </div>
  );
}
