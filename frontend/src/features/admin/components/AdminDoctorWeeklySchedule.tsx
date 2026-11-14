import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import type { DoctorSchedule, ScheduleSession } from "@/types/domain";
import { formatTime, sessionLabels, weekDayLabels } from "@/utils/format";

interface AdminDoctorWeeklyScheduleProps {
  schedules: DoctorSchedule[];
  onEdit: (schedule: DoctorSchedule) => void;
  onDelete: (schedule: DoctorSchedule) => void;
}

const WORK_WEEK_DAYS = [1, 2, 3, 4, 5, 6] as const;
const SESSION_ORDER: ScheduleSession[] = ["MORNING", "AFTERNOON"];

export function AdminDoctorWeeklySchedule({
  schedules,
  onEdit,
  onDelete,
}: AdminDoctorWeeklyScheduleProps) {
  const schedulesByDay = WORK_WEEK_DAYS.map((dayOfWeek) => ({
    dayOfWeek,
    schedules: SESSION_ORDER.map((session) =>
      schedules.find(
        (schedule) =>
          schedule.dayOfWeek === dayOfWeek && schedule.session === session,
      ),
    ).filter((schedule): schedule is DoctorSchedule => Boolean(schedule)),
  })).filter((day) => day.schedules.length > 0);

  return (
    <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
      {schedulesByDay.map((day) => (
        <section
          key={day.dayOfWeek}
          className="overflow-hidden rounded-xl border border-slate-200"
        >
          <h3 className="border-b border-slate-200 bg-slate-50 px-4 py-3 font-bold text-ink">
            {weekDayLabels[day.dayOfWeek]}
          </h3>
          <div className="divide-y divide-slate-100">
            {day.schedules.map((schedule) => (
              <div
                key={schedule.id}
                className="flex items-center justify-between gap-3 px-4 py-3"
              >
                <div>
                  <p className="font-semibold text-ink">
                    {sessionLabels[schedule.session]}
                  </p>
                  <p className="mt-1 text-sm text-muted">
                    {formatTime(schedule.startTime)}–
                    {formatTime(schedule.endTime)}
                  </p>
                </div>
                <div className="flex shrink-0 gap-1">
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => onEdit(schedule)}
                    aria-label={`Sửa ${sessionLabels[schedule.session]} ${weekDayLabels[schedule.dayOfWeek]}`}
                  >
                    Sửa
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => onDelete(schedule)}
                    aria-label={`Xóa ${sessionLabels[schedule.session]} ${weekDayLabels[schedule.dayOfWeek]}`}
                  >
                    <Trash2 className="h-4 w-4 text-red-600" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
