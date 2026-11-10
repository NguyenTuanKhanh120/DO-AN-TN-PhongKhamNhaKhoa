import { Clock3 } from "lucide-react";
import type { ScheduleSession, WalkInAvailability } from "@/types/domain";
import { cn } from "@/utils/cn";
import { formatQueueLabel, formatSessionWindow, formatTime } from "@/utils/format";

export interface WalkInCaseSelection {
  session: ScheduleSession;
  queueNumber: number;
  estimatedStartTime: string;
}

const statusLabels = {
  AVAILABLE: "Còn ca",
  FULL: "Hết ca",
  NOT_WORKING: "Không làm việc",
  UNAVAILABLE: "Đã kết thúc",
} as const;

export function WalkInAvailabilityPicker({
  availability,
  selected,
  onSelect,
}: {
  availability: WalkInAvailability;
  selected: WalkInCaseSelection | null;
  onSelect: (value: WalkInCaseSelection) => void;
}) {
  return (
    <div className="space-y-4">
      {availability.sessions.map((session) => (
        <section key={session.session} className="rounded-xl border border-slate-200 p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="font-bold text-ink">
              {formatSessionWindow(session.session, session.sessionStartTime, session.sessionEndTime)}
            </h3>
            <span className={cn(
              "rounded-full px-2.5 py-1 text-xs font-semibold",
              session.status === "AVAILABLE" ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-slate-600",
            )}>
              {statusLabels[session.status]}
            </span>
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            {session.cases.map((appointmentCase) => {
              const active = selected?.session === session.session && selected.queueNumber === appointmentCase.queueNumber;
              const stateLabel = appointmentCase.available
                ? "Còn trống"
                : session.status === "UNAVAILABLE"
                  ? "Buổi đã kết thúc"
                  : session.status === "NOT_WORKING"
                    ? "Không có lịch làm việc"
                    : "Đã có bệnh nhân";
              return (
                <button
                  key={`${session.session}-${appointmentCase.queueNumber}`}
                  type="button"
                  disabled={!appointmentCase.available}
                  aria-label={`${formatQueueLabel(appointmentCase.queueNumber)} dự kiến ${formatTime(appointmentCase.estimatedStartTime)} - ${stateLabel}`}
                  onClick={() => onSelect({
                    session: session.session,
                    queueNumber: appointmentCase.queueNumber,
                    estimatedStartTime: appointmentCase.estimatedStartTime,
                  })}
                  className={cn(
                    "rounded-xl border p-3 text-left transition disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400",
                    active ? "border-primary-600 bg-primary-700 text-white" : "border-slate-200 bg-white",
                    appointmentCase.available && !active && "hover:border-primary-300",
                  )}
                >
                  <span className="flex items-center justify-between gap-2 font-bold">
                    {formatQueueLabel(appointmentCase.queueNumber)}
                    <span className={cn("text-xs", active ? "text-primary-50" : appointmentCase.available ? "text-emerald-700" : "text-slate-500")}>
                      {stateLabel}
                    </span>
                  </span>
                  <span className={cn("mt-1 flex items-center gap-1 text-sm", active ? "text-primary-50" : "text-muted")}>
                    <Clock3 className="h-4 w-4" /> Dự kiến {formatTime(appointmentCase.estimatedStartTime)}
                  </span>
                </button>
              );
            })}
          </div>
        </section>
      ))}
    </div>
  );
}
