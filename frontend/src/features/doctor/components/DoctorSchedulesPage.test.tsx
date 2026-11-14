import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Appointment, AppointmentStatus, DoctorSchedule, ScheduleSession } from "@/types/domain";
import DoctorSchedulesPage from "./DoctorSchedulesPage";

const doctorMocks = vi.hoisted(() => ({
  schedules: [] as DoctorSchedule[],
  appointments: [] as Appointment[],
  scheduleLoading: false,
  appointmentLoading: false,
  scheduleError: null as Error | null,
  appointmentError: null as Error | null,
  refetchSchedules: vi.fn(),
  refetchAppointments: vi.fn(),
  useWeeklyAppointments: vi.fn(),
}));

vi.mock("@/utils/date", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/utils/date")>();
  return { ...actual, clinicToday: () => "2026-11-10" };
});

vi.mock("../hooks/useDoctorData", () => ({
  useDoctorSchedules: () => ({
    data: doctorMocks.scheduleLoading || doctorMocks.scheduleError ? undefined : doctorMocks.schedules,
    isLoading: doctorMocks.scheduleLoading,
    error: doctorMocks.scheduleError,
    refetch: doctorMocks.refetchSchedules,
  }),
  useDoctorWeeklyAppointments: (fromDate: string, toDate: string) => {
    doctorMocks.useWeeklyAppointments(fromDate, toDate);
    return {
      data: doctorMocks.appointmentLoading || doctorMocks.appointmentError ? undefined : doctorMocks.appointments,
      isLoading: doctorMocks.appointmentLoading,
      error: doctorMocks.appointmentError,
      refetch: doctorMocks.refetchAppointments,
    };
  },
}));

const sessions: Array<{ session: ScheduleSession; startTime: string; endTime: string }> = [
  { session: "MORNING", startTime: "08:00:00", endTime: "11:00:00" },
  { session: "AFTERNOON", startTime: "14:00:00", endTime: "17:00:00" },
];

function weeklySchedules(): DoctorSchedule[] {
  return Array.from({ length: 6 }, (_, index) => index + 1).flatMap((dayOfWeek) =>
    sessions.map(({ session, startTime, endTime }) => ({
      id: `schedule-${dayOfWeek}-${session}`,
      doctorId: "doctor-1",
      dayOfWeek,
      session,
      startTime,
      endTime,
      createdAt: "2026-11-01T00:00:00Z",
      updatedAt: "2026-11-01T00:00:00Z",
    })),
  );
}

function appointment({
  id,
  date,
  session,
  queueNumber,
  patientName,
  serviceName,
  status = "BOOKED",
}: {
  id: string;
  date: string;
  session: ScheduleSession;
  queueNumber: number;
  patientName: string;
  serviceName: string;
  status?: AppointmentStatus;
}): Appointment {
  const estimatedStartTime = session === "MORNING"
    ? (queueNumber === 1 ? "08:00:00" : "09:30:00")
    : (queueNumber === 1 ? "14:00:00" : "15:30:00");
  return {
    id,
    patientId: `patient-${id}`,
    patientFullName: patientName,
    patientPhone: "0901234567",
    doctorId: "doctor-1",
    doctorFullName: "Bác sĩ An",
    doctorSpecialization: "Nha khoa tổng quát",
    serviceId: `service-${id}`,
    serviceName,
    previousAppointmentId: null,
    appointmentDate: date,
    session,
    queueNumber,
    sessionStartTime: session === "MORNING" ? "08:00:00" : "14:00:00",
    sessionEndTime: session === "MORNING" ? "11:00:00" : "17:00:00",
    estimatedStartTime,
    estimatedStartAt: `${date}T${estimatedStartTime}+07:00`,
    status,
    checkedInAt: null,
    actualStartAt: null,
    actualEndAt: null,
    cancellationReason: status === "CANCELLED" ? "Bệnh nhân yêu cầu" : null,
    cancellationSource: status === "CANCELLED" ? "PATIENT_REQUEST" : null,
    cancelledAt: status === "CANCELLED" ? `${date}T06:00:00+07:00` : null,
    createdAt: `${date}T00:00:00Z`,
    updatedAt: `${date}T01:00:00Z`,
  };
}

function renderPage() {
  return render(<DoctorSchedulesPage />);
}

describe("DoctorSchedulesPage", () => {
  beforeEach(() => {
    doctorMocks.schedules = weeklySchedules();
    doctorMocks.appointments = [];
    doctorMocks.scheduleLoading = false;
    doctorMocks.appointmentLoading = false;
    doctorMocks.scheduleError = null;
    doctorMocks.appointmentError = null;
    doctorMocks.refetchSchedules.mockReset();
    doctorMocks.refetchSchedules.mockResolvedValue(undefined);
    doctorMocks.refetchAppointments.mockReset();
    doctorMocks.refetchAppointments.mockResolvedValue(undefined);
    doctorMocks.useWeeklyAppointments.mockReset();
  });

  afterEach(() => cleanup());

  it("gộp sáng và chiều thành đúng 6 card từ Thứ Hai đến Thứ Bảy", () => {
    renderPage();

    const grid = screen.getByTestId("weekly-schedule-grid");
    const cards = within(grid).getAllByTestId("schedule-day-card");
    expect(cards).toHaveLength(6);
    const mondayCard = cards[0];
    if (!mondayCard) throw new Error("Thiếu card Thứ Hai");
    expect(grid).toHaveClass("md:grid-cols-2", "xl:grid-cols-3");
    expect(screen.getAllByText("Thứ Hai")).toHaveLength(1);
    expect(screen.getByText("09/11/2026")).toBeInTheDocument();
    expect(screen.getByText("14/11/2026")).toBeInTheDocument();

    expect(within(mondayCard).getByText("Buổi sáng")).toBeInTheDocument();
    expect(within(mondayCard).getByText("08:00–11:00")).toBeInTheDocument();
    expect(within(mondayCard).getByText("Buổi chiều")).toBeInTheDocument();
    expect(within(mondayCard).getByText("14:00–17:00")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Sửa|Xóa/ })).not.toBeInTheDocument();
    expect(doctorMocks.useWeeklyAppointments).toHaveBeenCalledWith("2026-11-09", "2026-11-14");
  });

  it("tô đỏ ngày có lịch, giữ trung tính ngày trống và ngày chỉ có CANCELLED", () => {
    doctorMocks.appointments = [
      appointment({ id: "monday", date: "2026-11-09", session: "MORNING", queueNumber: 1, patientName: "Nguyễn Văn A", serviceName: "Trám răng" }),
      appointment({ id: "tuesday-cancelled", date: "2026-11-10", session: "MORNING", queueNumber: 1, patientName: "Trần Thị B", serviceName: "Cạo vôi", status: "CANCELLED" }),
    ];
    renderPage();

    const monday = screen.getByRole("button", { name: "Xem lịch khám Thứ Hai ngày 09/11/2026" });
    const tuesday = screen.getByRole("button", { name: "Xem lịch khám Thứ Ba ngày 10/11/2026" });
    const wednesday = screen.getByRole("button", { name: "Xem lịch khám Thứ Tư ngày 11/11/2026" });

    expect(monday).toHaveAttribute("data-has-appointments", "true");
    expect(monday).toHaveClass("border-red-200", "bg-red-50/50");
    expect(within(monday).getByText("Có lịch khám")).toBeInTheDocument();
    expect(tuesday).toHaveAttribute("data-has-appointments", "false");
    expect(tuesday).toHaveClass("border-slate-200", "bg-white");
    expect(within(tuesday).getByText("Chưa có lịch khám")).toBeInTheDocument();
    expect(wednesday).toHaveAttribute("data-has-appointments", "false");
  });

  it("click ngày sẽ hiển thị đúng lịch ngày đó theo sáng, chiều và thứ tự ca", async () => {
    doctorMocks.appointments = [
      appointment({ id: "morning-2", date: "2026-11-09", session: "MORNING", queueNumber: 2, patientName: "Trần Thị B", serviceName: "Cạo vôi răng" }),
      appointment({ id: "morning-1", date: "2026-11-09", session: "MORNING", queueNumber: 1, patientName: "Nguyễn Văn A", serviceName: "Trám răng" }),
      appointment({ id: "afternoon-1", date: "2026-11-09", session: "AFTERNOON", queueNumber: 1, patientName: "Lê Văn C", serviceName: "Nhổ răng", status: "COMPLETED" }),
      appointment({ id: "other-day", date: "2026-11-10", session: "MORNING", queueNumber: 1, patientName: "Bệnh nhân thứ Ba", serviceName: "Khám răng" }),
    ];
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Xem lịch khám Thứ Hai ngày 09/11/2026" }));

    expect(screen.getByRole("heading", { name: "Lịch khám Thứ Hai - 09/11/2026" })).toBeInTheDocument();
    const morning = screen.getByTestId("appointment-session-morning");
    const afternoon = screen.getByTestId("appointment-session-afternoon");
    const morningSlots = within(morning).getAllByTestId(/appointment-slot-morning-/);
    const firstMorningSlot = morningSlots[0];
    const secondMorningSlot = morningSlots[1];
    if (!firstMorningSlot || !secondMorningSlot) throw new Error("Thiếu ca buổi sáng");
    expect(firstMorningSlot).toHaveAttribute("data-testid", "appointment-slot-morning-1");
    expect(secondMorningSlot).toHaveAttribute("data-testid", "appointment-slot-morning-2");
    expect(within(firstMorningSlot).getByText("Nguyễn Văn A")).toBeInTheDocument();
    expect(within(secondMorningSlot).getByText("Trần Thị B")).toBeInTheDocument();
    expect(within(afternoon).getByText("Lê Văn C")).toBeInTheDocument();
    expect(within(afternoon).getByText("Hoàn thành")).toBeInTheDocument();
    expect(within(afternoon).getByTestId("appointment-slot-afternoon-2")).toHaveTextContent("Trống");
    expect(screen.queryByText("Bệnh nhân thứ Ba")).not.toBeInTheDocument();
  });

  it("click ngày trống vẫn chọn card và hiển thị đủ bốn ca trống", async () => {
    const user = userEvent.setup();
    renderPage();
    const tuesday = screen.getByRole("button", { name: "Xem lịch khám Thứ Ba ngày 10/11/2026" });

    await user.click(tuesday);

    expect(tuesday).toHaveAttribute("aria-pressed", "true");
    expect(tuesday).toHaveClass("ring-4", "ring-primary-200", "bg-white");
    expect(screen.getByText("Chưa có lịch khám trong ngày này.")).toBeInTheDocument();
    expect(within(screen.getByTestId("selected-day-detail")).getAllByText("Trống")).toHaveLength(4);
  });

  it("xóa ngày đã chọn khi chuyển tuần trước hoặc tuần sau", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Xem lịch khám Thứ Hai ngày 09/11/2026" }));
    expect(screen.getByTestId("selected-day-detail")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tuần sau" }));
    expect(screen.queryByTestId("selected-day-detail")).not.toBeInTheDocument();
    expect(screen.getByText("Tuần 16/11/2026 – 21/11/2026")).toBeInTheDocument();
    expect(doctorMocks.useWeeklyAppointments).toHaveBeenLastCalledWith("2026-11-16", "2026-11-21");

    await user.click(screen.getByRole("button", { name: "Xem lịch khám Thứ Hai ngày 16/11/2026" }));
    await user.click(screen.getByRole("button", { name: "Tuần trước" }));
    expect(screen.queryByTestId("selected-day-detail")).not.toBeInTheDocument();
    expect(screen.getByText("Tuần 09/11/2026 – 14/11/2026")).toBeInTheDocument();
    expect(doctorMocks.useWeeklyAppointments).toHaveBeenLastCalledWith("2026-11-09", "2026-11-14");
  });

  it("hiển thị loading khi dữ liệu tuần đang tải", () => {
    doctorMocks.appointmentLoading = true;
    renderPage();

    expect(screen.getByLabelText("Đang tải")).toBeInTheDocument();
    expect(screen.queryByTestId("weekly-schedule-grid")).not.toBeInTheDocument();
  });

  it("hiển thị lỗi và thử tải lại cả lịch làm việc lẫn lịch khám", async () => {
    doctorMocks.appointmentError = new Error("Không thể tải lịch khám");
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByText("Không thể tải dữ liệu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(doctorMocks.refetchSchedules).toHaveBeenCalledOnce();
    expect(doctorMocks.refetchAppointments).toHaveBeenCalledOnce();
  });
});
