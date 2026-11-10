import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import BookingPage from "./BookingPage";

const publicMocks = vi.hoisted(() => ({
  refetch: vi.fn(),
  bookAppointment: vi.fn().mockResolvedValue({ id: "appointment-1" }),
}));

vi.mock("@/contexts/ToastContext", () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

vi.mock("@/features/public/hooks/usePublicData", () => ({
  useServices: () => ({
    data: { content: [{ id: "service-1", name: "Khám tổng quát", description: null, price: 200000, imageUrl: null, active: true }] },
    isLoading: false,
    error: null,
  }),
  useServiceDoctors: (serviceId?: string) => ({
    data: serviceId ? [{ id: "doctor-1", fullName: "Bác sĩ An", specialization: "Nha khoa tổng quát", workingStatus: "ACTIVE" }] : [],
    isLoading: false,
    error: null,
  }),
  useDoctorCalendar: (doctorId?: string) => ({
    data: doctorId ? { days: [{ date: "2026-11-02", status: "AVAILABLE" }] } : undefined,
    isLoading: false,
    error: null,
    refetch: publicMocks.refetch,
  }),
  useAvailableSlots: (_doctorId?: string, _serviceId?: string, date?: string) => ({
    data: date ? [{
      date: "2026-11-02",
      session: "MORNING",
      queueNumber: 1,
      sessionStartTime: "08:00:00",
      sessionEndTime: "11:00:00",
      estimatedStartTime: "08:00:00",
      estimatedStartAt: "2026-11-02T08:00:00+07:00",
    }] : [],
    isLoading: false,
    isSuccess: Boolean(date),
    error: null,
    refetch: publicMocks.refetch,
  }),
}));

vi.mock("../hooks/usePatientData", () => ({
  useBookAppointment: () => ({ mutateAsync: publicMocks.bookAppointment, isPending: false }),
  usePatientAppointments: () => ({ data: { content: [] }, refetch: vi.fn() }),
}));

function Harness() {
  return (
    <>
      <Link to="/patient/book-appointment?serviceId=service-1&doctorId=doctor-1&date=2026-11-02&session=MORNING&queueNumber=1">
        Áp dụng ca chatbot
      </Link>
      <BookingPage />
    </>
  );
}

describe("BookingPage chatbot prefill", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("đồng bộ query mới và chỉ chọn slot có trong dữ liệu khả dụng", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/patient/book-appointment"]}>
        <Routes>
          <Route path="/patient/book-appointment" element={<Harness />} />
        </Routes>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole("link", { name: "Áp dụng ca chatbot" }));

    expect(await screen.findByText("5. Xác nhận lịch khám")).toBeInTheDocument();
    expect(screen.getAllByText("Khám tổng quát")).toHaveLength(2);
    expect(screen.getAllByText("Bác sĩ An")).toHaveLength(2);
    expect(screen.getByText("Ca 1 · dự kiến 08:00")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Xác nhận đặt lịch" }));

    expect(publicMocks.bookAppointment).toHaveBeenCalledWith({
      serviceId: "service-1",
      doctorId: "doctor-1",
      appointmentDate: "2026-11-02",
      session: "MORNING",
      queueNumber: 1,
      previousAppointmentId: null,
    });
  });
});
