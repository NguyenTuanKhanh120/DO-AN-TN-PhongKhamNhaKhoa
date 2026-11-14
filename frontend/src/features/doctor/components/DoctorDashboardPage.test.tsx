import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import DoctorDashboardPage from "./DoctorDashboardPage";

const mocks = vi.hoisted(() => ({ start: vi.fn(), toast: vi.fn() }));
vi.mock("@/contexts/ToastContext", () => ({ useToast: () => ({ showToast: mocks.toast }) }));
vi.mock("../hooks/useDoctorData", () => ({
  useStartExamination: () => ({ mutateAsync: mocks.start, isPending: false }),
  useDoctorWeeklyAppointments: () => ({
    data: [
      { id: "afternoon", patientFullName: "Bệnh nhân chiều", session: "AFTERNOON", queueNumber: 1, status: "BOOKED" },
      { id: "second", patientFullName: "Bệnh nhân ca hai", session: "MORNING", queueNumber: 2, status: "BOOKED" },
      { id: "done", patientFullName: "Bệnh nhân hoàn thành", session: "MORNING", queueNumber: 1, status: "COMPLETED" },
      { id: "first", patientFullName: "Bệnh nhân đầu tiên", session: "MORNING", queueNumber: 1, status: "BOOKED" },
    ].map((item) => ({ ...item, serviceName: "Khám răng", appointmentDate: "2026-11-10", estimatedStartTime: "08:00:00", estimatedStartAt: "2026-11-10T08:00:00+07:00" })),
    isLoading: false, error: null, refetch: vi.fn(),
  }),
}));

beforeEach(() => { mocks.start.mockReset(); mocks.toast.mockReset(); });
afterEach(cleanup);

it("xếp buổi sáng và ca nhỏ trước, tách các ca hoàn thành", async () => {
  const user = userEvent.setup();
  render(<MemoryRouter><DoctorDashboardPage /></MemoryRouter>);
  const links = screen.getAllByRole("link", { name: /Bệnh nhân/ });
  expect(links.map((link) => link.textContent)).toEqual([
    expect.stringContaining("Bệnh nhân đầu tiên"),
    expect.stringContaining("Bệnh nhân ca hai"),
    expect.stringContaining("Bệnh nhân chiều"),
  ]);
  const startButtons = screen.getAllByRole("button", { name: "Bắt đầu khám" });
  expect(startButtons[0]).toBeEnabled();
  expect(startButtons[1]).toBeDisabled();
  expect(startButtons[2]).toBeDisabled();
  expect(screen.queryByRole("link", { name: /Bệnh nhân hoàn thành/ })).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Hoàn thành (1)" }));
  expect(screen.getByRole("link", { name: /Bệnh nhân hoàn thành/ })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Bắt đầu khám" })).not.toBeInTheDocument();
});

it("gửi đúng ca đầu hàng chờ và hiển thị lỗi nếu chưa thể bắt đầu", async () => {
  mocks.start.mockRejectedValue(new Error("Chưa đến buổi khám"));
  const user = userEvent.setup();
  render(<MemoryRouter><DoctorDashboardPage /></MemoryRouter>);
  const firstRow = screen.getByRole("link", { name: /Bệnh nhân đầu tiên/ }).parentElement;
  if (!firstRow) throw new Error("Thiếu hàng bệnh nhân");
  await user.click(within(firstRow).getByRole("button", { name: "Bắt đầu khám" }));
  expect(mocks.start).toHaveBeenCalledWith("first");
  expect(mocks.toast).toHaveBeenCalledWith(expect.any(String), "error");
});
