import { CalendarDays, ChevronLeft, ChevronRight, Info, Sun, Sunset } from "lucide-react";
import { useMemo, useState } from "react";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import type { DoctorSchedule, ScheduleSession } from "@/types/domain";
import { cn } from "@/utils/cn";
import { addCalendarDays, clinicToday } from "@/utils/date";
import { formatQueueLabel, formatTime, sessionLabels, weekDayLabels } from "@/utils/format";
import { useDoctorSchedules, useDoctorWeeklyAppointments } from "../hooks/useDoctorData";
import {
  buildDoctorWeekDays,
  findSlotAppointment,
  formatNumericDate,
  SESSION_ORDER,
  sessionSlotTimes,
  startOfClinicWeek,
  type DoctorWeekDay,
} from "../utils/weeklySchedule";

export default function DoctorSchedulesPage() {
  const [weekStart, setWeekStart] = useState(() => startOfClinicWeek(clinicToday()));
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const weekEnd = addCalendarDays(weekStart, 5);
  const schedules = useDoctorSchedules();
  const appointments = useDoctorWeeklyAppointments(weekStart, weekEnd);
  const weekDays = useMemo(
    () => buildDoctorWeekDays(weekStart, schedules.data ?? [], appointments.data ?? []),
    [appointments.data, schedules.data, weekStart],
  );
  const selectedDay = weekDays.find((day) => day.date === selectedDate) ?? null;

  const moveWeek = (days: number) => {
    setWeekStart((current) => addCalendarDays(current, days));
    setSelectedDate(null);
  };

  return (
    <div>
      <PageHeader
        title="Lịch làm việc tuần"
        description="Lịch do quản trị viên cấu hình. Bác sĩ chỉ có quyền xem."
      />

      <div className="mb-6 flex items-center justify-center gap-3">
        <Button variant="ghost" size="sm" aria-label="Tuần trước" onClick={() => moveWeek(-7)}>
          <ChevronLeft className="h-5 w-5" />
        </Button>
        <p className="min-w-0 text-center text-sm font-bold text-ink sm:text-base">
          Tuần {formatNumericDate(weekStart)} – {formatNumericDate(weekEnd)}
        </p>
        <Button variant="ghost" size="sm" aria-label="Tuần sau" onClick={() => moveWeek(7)}>
          <ChevronRight className="h-5 w-5" />
        </Button>
      </div>

      <AsyncStateView
        loading={schedules.isLoading || appointments.isLoading}
        error={schedules.error || appointments.error}
        empty={!schedules.data?.length}
        onRetry={() => void Promise.all([schedules.refetch(), appointments.refetch()])}
        emptyTitle="Chưa có lịch làm việc"
      >
        <div data-testid="weekly-schedule-grid" className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {weekDays.map((day) => {
            const selected = selectedDate === day.date;
            return (
              <button
                key={day.date}
                type="button"
                data-testid="schedule-day-card"
                data-has-appointments={day.hasAppointments}
                data-selected={selected}
                aria-pressed={selected}
                aria-label={`Xem lịch khám ${weekDayLabels[day.dayOfWeek]} ngày ${formatNumericDate(day.date)}`}
                onClick={() => setSelectedDate(day.date)}
                className={cn(
                  "rounded-2xl border p-5 text-left shadow-card transition hover:-translate-y-0.5 hover:shadow-lg focus:outline-none focus-visible:ring-4 focus-visible:ring-primary-200",
                  day.hasAppointments ? "border-red-200 bg-red-50/50" : "border-slate-200 bg-white",
                  selected && "ring-4 ring-primary-200",
                )}
              >
                <span className="flex items-start justify-between gap-3">
                  <span className="flex min-w-0 items-start gap-3">
                    <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-primary-50 text-primary-700">
                      <CalendarDays className="h-5 w-5" />
                    </span>
                    <span>
                      <span className="block font-bold text-ink">{weekDayLabels[day.dayOfWeek]}</span>
                      <span className="mt-1 block text-sm text-muted">{formatNumericDate(day.date)}</span>
                    </span>
                  </span>
                  <Badge tone={day.hasAppointments ? "danger" : "neutral"}>
                    {day.hasAppointments ? "Có lịch khám" : "Chưa có lịch khám"}
                  </Badge>
                </span>

                <span className="mt-5 block space-y-3 border-t border-slate-200/80 pt-4">
                  {SESSION_ORDER.map((session) => (
                    <DaySessionSummary key={session} session={session} schedule={day.schedules[session]} />
                  ))}
                </span>
              </button>
            );
          })}
        </div>

        <p className="mt-4 flex items-start gap-2 text-sm text-muted">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-primary-700" />
          <span>Thẻ màu đỏ là ngày có lịch khám. Nhấn vào một ngày để xem chi tiết các ca.</span>
        </p>

        {selectedDay && <SelectedDayAppointments day={selectedDay} />}
      </AsyncStateView>
    </div>
  );
}

function DaySessionSummary({ session, schedule }: { session: ScheduleSession; schedule?: DoctorSchedule }) {
  const Icon = session === "MORNING" ? Sun : Sunset;
  return (
    <span className="flex items-center gap-3">
      <span className={cn(
        "grid h-9 w-9 shrink-0 place-items-center rounded-lg",
        session === "MORNING" ? "bg-amber-50 text-amber-600" : "bg-orange-50 text-orange-600",
      )}>
        <Icon className="h-4 w-4" />
      </span>
      <span>
        <span className="block text-sm font-semibold text-ink">{sessionLabels[session]}</span>
        <span className="block text-xs text-muted">
          {schedule ? `${formatTime(schedule.startTime)}–${formatTime(schedule.endTime)}` : "Không có lịch làm việc"}
        </span>
      </span>
    </span>
  );
}

function SelectedDayAppointments({ day }: { day: DoctorWeekDay }) {
  return (
    <Card data-testid="selected-day-detail" className="mt-8 p-5 sm:p-6">
      <div className="border-b border-slate-200 pb-4">
        <h2 className="text-lg font-bold text-ink">
          Lịch khám {weekDayLabels[day.dayOfWeek]} - {formatNumericDate(day.date)}
        </h2>
        {day.appointments.length === 0 && (
          <p className="mt-2 text-sm text-muted">Chưa có lịch khám trong ngày này.</p>
        )}
      </div>

      <div className="mt-5 space-y-6">
        {SESSION_ORDER.map((session) => {
          const schedule = day.schedules[session];
          if (!schedule) return null;
          const Icon = session === "MORNING" ? Sun : Sunset;
          return (
            <section key={session} data-testid={`appointment-session-${session.toLowerCase()}`}>
              <div className="mb-3 flex items-center gap-2">
                <Icon className={cn("h-5 w-5", session === "MORNING" ? "text-amber-600" : "text-orange-600")} />
                <h3 className="font-bold text-ink">{sessionLabels[session]}</h3>
                <span className="text-sm text-muted">
                  {formatTime(schedule.startTime)}–{formatTime(schedule.endTime)}
                </span>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                {sessionSlotTimes[session].map((estimatedStartTime, index) => {
                  const queueNumber = index + 1;
                  const appointment = findSlotAppointment(day.appointments, session, queueNumber);
                  return (
                    <div
                      key={queueNumber}
                      data-testid={`appointment-slot-${session.toLowerCase()}-${queueNumber}`}
                      className="rounded-xl border border-slate-200 bg-white p-4"
                    >
                      <p className="text-sm font-bold text-primary-800">
                        {formatQueueLabel(queueNumber)} · dự kiến {appointment ? formatTime(appointment.estimatedStartTime) : estimatedStartTime}
                      </p>
                      {appointment ? (
                        <div className="mt-3">
                          <p className="font-semibold text-ink">{appointment.patientFullName}</p>
                          <p className="mt-1 text-sm text-muted">{appointment.serviceName}</p>
                          <div className="mt-3"><StatusBadge status={appointment.status} /></div>
                        </div>
                      ) : (
                        <p className="mt-3 text-sm font-semibold text-slate-500">Trống</p>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>
          );
        })}
      </div>
    </Card>
  );
}
