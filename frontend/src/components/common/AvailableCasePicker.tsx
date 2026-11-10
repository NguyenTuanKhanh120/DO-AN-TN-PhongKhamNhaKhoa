import { Clock3, Info } from "lucide-react";
import type { AvailableSlot, ScheduleSession } from "@/types/domain";
import {
  appointmentArrivalNotice,
  formatQueueLabel,
  formatSessionWindow,
  formatTime,
} from "@/utils/format";

const sessions: ScheduleSession[] = ["MORNING", "AFTERNOON"];

export function availableCaseKey(slot: Pick<AvailableSlot, "session" | "queueNumber">): string {
  return `${slot.session}-${slot.queueNumber}`;
}

export function AvailableCasePicker({
  slots,
  selected,
  onSelect,
  disabledSessions = [],
}: {
  slots: AvailableSlot[];
  selected: AvailableSlot | null;
  onSelect: (slot: AvailableSlot) => void;
  disabledSessions?: ScheduleSession[];
}) {
  return (
    <div className="space-y-4">
      {sessions.map((session) => {
        const sessionSlots = slots
          .filter((slot) => slot.session === session)
          .sort((left, right) => left.queueNumber - right.queueNumber);
        if (!sessionSlots.length) return null;
        const firstSlot = sessionSlots[0];
        if (!firstSlot) return null;
        const disabled = disabledSessions.includes(session);
        return (
          <section key={session} className="rounded-xl border border-slate-200 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="font-bold text-ink">
                {formatSessionWindow(session, firstSlot.sessionStartTime, firstSlot.sessionEndTime)}
              </h3>
              {disabled && <span className="text-xs font-semibold text-amber-700">Bạn đã có lịch trong buổi này</span>}
            </div>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              {sessionSlots.map((slot) => {
                const active = selected ? availableCaseKey(selected) === availableCaseKey(slot) : false;
                return (
                  <button
                    key={availableCaseKey(slot)}
                    type="button"
                    disabled={disabled}
                    onClick={() => onSelect(slot)}
                    className={`rounded-xl border p-3 text-left transition disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400 ${active ? "border-primary-600 bg-primary-700 text-white" : "border-slate-200 bg-white hover:border-primary-300"}`}
                  >
                    <span className="block font-bold">{formatQueueLabel(slot.queueNumber)}</span>
                    <span className={`mt-1 flex items-center gap-1 text-sm ${active ? "text-primary-50" : "text-muted"}`}>
                      <Clock3 className="h-4 w-4" /> Dự kiến {formatTime(slot.estimatedStartTime)}
                    </span>
                  </button>
                );
              })}
            </div>
          </section>
        );
      })}
      <p className="flex gap-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
        <Info className="mt-0.5 h-4 w-4 shrink-0" /> {appointmentArrivalNotice}
      </p>
    </div>
  );
}
