import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import BookingPage from "./BookingPage";

const publicMocks = vi.hoisted(() => ({
  refetch: vi.fn(),
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
    data: date ? [{ startAt: "2026-11-02T08:00:00+07:00" }] : [],
    isLoading: false,
    isSuccess: Boolean(date),
    error: null,
    refetch: publicMocks.refetch,
  }),
}));

vi.mock("../hooks/usePatientData", () => ({
  useBookAppointment: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

function Harness() {
  return (
    <>
      <Link to="/patient/book-appointment?serviceId=service-1&doctorId=doctor-1&date=2026-11-02&scheduledStartAt=2026-11-02T08%3A00%3A00%2B07%3A00">
        Áp dụng slot chatbot
      </Link>
      <BookingPage />
    </>
  );
}

describe("BookingPage chatbot prefill", () => {
  afterEach(cleanup);

  it("đồng bộ query mới và chỉ chọn slot có trong dữ liệu khả dụng", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/patient/book-appointment"]}>
        <Routes>
          <Route path="/patient/book-appointment" element={<Harness />} />
        </Routes>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole("link", { name: "Áp dụng slot chatbot" }));

    expect(await screen.findByText("5. Xác nhận lịch khám")).toBeInTheDocument();
    expect(screen.getAllByText("Khám tổng quát")).toHaveLength(2);
    expect(screen.getAllByText("Bác sĩ An")).toHaveLength(2);
  });
});
