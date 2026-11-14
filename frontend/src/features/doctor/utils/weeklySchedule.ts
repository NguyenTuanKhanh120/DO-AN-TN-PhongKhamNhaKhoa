import type { Appointment, DoctorSchedule, ScheduleSession } from "@/types/domain";
import { addCalendarDays } from "@/utils/date";

export const WEEK_DAY_VALUES = [1, 2, 3, 4, 5, 6] as const;
export const SESSION_ORDER: readonly ScheduleSession[] = ["MORNING", "AFTERNOON"];

export const sessionSlotTimes: Record<ScheduleSession, readonly [string, string]> = {
  MORNING: ["08:00", "09:30"],
  AFTERNOON: ["14:00", "15:30"],
};

export interface DoctorWeekDay {
  date: string;
  dayOfWeek: number;
  schedules: Partial<Record<ScheduleSession, DoctorSchedule>>;
  appointments: Appointment[];
  hasAppointments: boolean;
}

export function startOfClinicWeek(date: string): string {
  const dayOfWeek = new Date(`${date}T00:00:00Z`).getUTCDay();
  return addCalendarDays(date, dayOfWeek === 0 ? -6 : 1 - dayOfWeek);
}

export function formatNumericDate(date: string): string {
  const [year, month, day] = date.split("-");
  return `${day}/${month}/${year}`;
}

export function buildDoctorWeekDays(
  weekStart: string,
  schedules: DoctorSchedule[],
  appointments: Appointment[],
): DoctorWeekDay[] {
  return WEEK_DAY_VALUES.map((dayOfWeek, index) => {
    const date = addCalendarDays(weekStart, index);
    const daySchedules = schedules.filter((schedule) => schedule.dayOfWeek === dayOfWeek);
    const dayAppointments = appointments.filter((appointment) => appointment.appointmentDate === date);

    return {
      date,
      dayOfWeek,
      schedules: Object.fromEntries(daySchedules.map((schedule) => [schedule.session, schedule])),
      appointments: dayAppointments,
      hasAppointments: dayAppointments.some((appointment) => appointment.status !== "CANCELLED"),
    };
  });
}

export function findSlotAppointment(
  appointments: Appointment[],
  session: ScheduleSession,
  queueNumber: number,
): Appointment | undefined {
  return appointments
    .filter((appointment) => appointment.session === session && appointment.queueNumber === queueNumber)
    .sort((left, right) => {
      const statusOrder = Number(left.status === "CANCELLED") - Number(right.status === "CANCELLED");
      return statusOrder || right.updatedAt.localeCompare(left.updatedAt);
    })[0];
}
