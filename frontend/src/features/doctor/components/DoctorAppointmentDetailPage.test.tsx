import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/services/api/errors";
import type { Appointment } from "@/types/domain";
import { formatMoney } from "@/utils/format";
import DoctorAppointmentDetailPage from "./DoctorAppointmentDetailPage";

const doctorMocks = vi.hoisted(() => ({
  appointment: undefined as Appointment | undefined,
  paymentError: null as Error | null,
  assignedServices: [{
    id: "service-1",
    name: "Cạo vôi răng",
    description: null,
    price: 350000,
    imageUrl: null,
    active: true,
  }],
  markNoShow: vi.fn(),
  updateItems: vi.fn(),
  showToast: vi.fn(),
}));

vi.mock("@/contexts/ToastContext", () => ({
  useToast: () => ({ showToast: doctorMocks.showToast }),
}));

vi.mock("@/hooks/useUnsavedChangesWarning", () => ({
  useUnsavedChangesWarning: vi.fn(),
}));

vi.mock("../hooks/useCachedDoctorAppointment", () => ({
  useCachedDoctorAppointment: () => doctorMocks.appointment,
}));

vi.mock("../hooks/useDoctorData", () => ({
  useMedicalRecord: () => ({
    data: undefined,
    error: new ApiError("Không tìm thấy bệnh án", "RESOURCE_NOT_FOUND", 404),
  }),
  useDoctorPayment: () => ({
    data: undefined,
    error: doctorMocks.paymentError,
  }),
  useDoctorAssignedServices: () => ({
    data: doctorMocks.assignedServices,
    isSuccess: true,
  }),
  useStartExamination: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useMarkNoShow: () => ({ mutateAsync: doctorMocks.markNoShow, isPending: false }),
  useCompleteExamination: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSaveMedicalRecord: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdatePaymentItems: () => ({
    mutateAsync: doctorMocks.updateItems,
    isPending: false,
  }),
}));

const appointment: Appointment = {
  id: "appointment-1",
  patientId: "patient-1",
  patientFullName: "Nguyễn Văn A",
  patientPhone: "0987671231",
  doctorId: "doctor-1",
  doctorFullName: "Bác sĩ Demo",
  doctorSpecialization: "Nha khoa tổng quát",
  serviceId: "service-1",
  serviceName: "Cạo vôi răng",
  previousAppointmentId: null,
  appointmentDate: "2026-11-10",
  session: "MORNING",
  queueNumber: 1,
  sessionStartTime: "08:00:00",
  sessionEndTime: "11:00:00",
  estimatedStartTime: "08:00:00",
  estimatedStartAt: "2026-11-10T08:00:00+07:00",
  status: "IN_PROGRESS",
  checkedInAt: null,
  actualStartAt: "2026-11-10T09:37:46+07:00",
  actualEndAt: null,
  cancellationReason: null,
  cancellationSource: null,
  cancelledAt: null,
  createdAt: "2026-11-10T02:30:00Z",
  updatedAt: "2026-11-10T02:37:46Z",
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/doctor/appointments/appointment-1"]}>
      <Routes>
        <Route
          path="/doctor/appointments/:id"
          element={<DoctorAppointmentDetailPage />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("DoctorAppointmentDetailPage", () => {
  beforeEach(() => {
    doctorMocks.appointment = appointment;
    doctorMocks.paymentError = new ApiError(
      "Không tìm thấy phiếu thanh toán",
      "RESOURCE_NOT_FOUND",
      404,
    );
    doctorMocks.updateItems.mockReset();
    doctorMocks.updateItems.mockResolvedValue(undefined);
    doctorMocks.markNoShow.mockReset();
    doctorMocks.markNoShow.mockResolvedValue(undefined);
    doctorMocks.showToast.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("điền sẵn dịch vụ đăng ký mà không hiển thị thông báo phụ hoặc lỗi 404", async () => {
    renderPage();

    expect(screen.queryByText("Không tìm thấy phiếu thanh toán")).not.toBeInTheDocument();
    expect(screen.queryByText(/Dịch vụ đăng ký được điền sẵn/)).not.toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole("combobox")).toHaveValue("service-1");
    });
    expect(screen.getByRole("spinbutton", { name: "" })).toHaveValue(1);
  });

  it("chỉ lưu dịch vụ điền sẵn sau khi bác sĩ xác nhận", async () => {
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("combobox")).toHaveValue("service-1");
    });
    expect(doctorMocks.updateItems).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Lưu dịch vụ thực tế" }));

    await waitFor(() => {
      expect(doctorMocks.updateItems).toHaveBeenCalledWith([
        { serviceId: "service-1", quantity: 1 },
      ]);
    });
  });

  it("cập nhật tổng tạm tính khi điền sẵn, đổi số lượng, thêm và xóa dịch vụ", async () => {
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText(formatMoney(350000).replace(/\s/g, " "))).toBeInTheDocument();
    const quantity = screen.getByRole("spinbutton");
    await user.clear(quantity);
    await user.type(quantity, "2");
    expect(screen.getByText(formatMoney(700000).replace(/\s/g, " "))).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thêm dịch vụ" }));
    const selects = screen.getAllByRole("combobox");
    const addedSelect = selects[1];
    if (!addedSelect) throw new Error("Thiếu dịch vụ thêm mới");
    await user.selectOptions(addedSelect, "service-1");
    expect(screen.getByText(formatMoney(1050000).replace(/\s/g, " "))).toBeInTheDocument();
    const removeButtons = screen.getAllByRole("button", { name: "Xóa dịch vụ" });
    const addedRemoveButton = removeButtons[1];
    if (!addedRemoveButton) throw new Error("Thiếu nút xóa dịch vụ");
    await user.click(addedRemoveButton);
    expect(screen.getByText(formatMoney(700000).replace(/\s/g, " "))).toBeInTheDocument();
    expect(doctorMocks.updateItems).not.toHaveBeenCalled();
  });

  it("cho phép xác nhận bệnh nhân không đến khi lịch đang BOOKED", async () => {
    doctorMocks.appointment = { ...appointment, status: "BOOKED", actualStartAt: null };
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole("button", { name: "Không đến" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bắt đầu khám" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Không đến" }));

    expect(screen.getByRole("heading", { name: "Đánh dấu không đến" })).toBeInTheDocument();
    expect(screen.getByText("Có thể xác nhận từ giờ hẹn nếu bệnh nhân chưa đến.")).toBeInTheDocument();
    expect(screen.queryByText(/Backend sẽ kiểm tra/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Xác nhận không đến" }));

    await waitFor(() => expect(doctorMocks.markNoShow).toHaveBeenCalledWith("appointment-1"));
    expect(doctorMocks.showToast).toHaveBeenCalledWith("Đã đánh dấu bệnh nhân không đến", "success");
    expect(screen.getByText("Không đến")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Bắt đầu khám" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xác nhận không đến" })).not.toBeInTheDocument();
  });

  it("giữ nguyên lịch BOOKED và hiển thị lỗi khi đánh dấu trước giờ hẹn", async () => {
    doctorMocks.appointment = { ...appointment, status: "BOOKED", actualStartAt: null };
    doctorMocks.markNoShow.mockRejectedValue(new ApiError(
      "Chỉ có thể xác nhận NO_SHOW từ giờ hẹn",
      "RESOURCE_CONFLICT",
      409,
    ));
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Không đến" }));
    await user.click(screen.getByRole("button", { name: "Xác nhận không đến" }));

    await waitFor(() => expect(doctorMocks.showToast).toHaveBeenCalledWith(
      "Chỉ có thể xác nhận NO_SHOW từ giờ hẹn",
      "error",
    ));
    expect(screen.getByText("Đã đặt lịch")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xác nhận không đến" })).toBeInTheDocument();
  });
});
