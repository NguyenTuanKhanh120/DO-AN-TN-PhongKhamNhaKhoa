import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/services/api/errors";
import StaffWalkInPage from "./StaffWalkInPage";

const staffMocks = vi.hoisted(() => ({
  patients: [] as Array<Record<string, unknown>>,
  createPatient: vi.fn(),
  createWalkIn: vi.fn(),
  refetchAvailability: vi.fn(),
  showToast: vi.fn(),
}));

vi.mock("@/hooks/useDebouncedValue", () => ({
  useDebouncedValue: <T,>(value: T) => value,
}));

vi.mock("@/contexts/ToastContext", () => ({
  useToast: () => ({ showToast: staffMocks.showToast }),
}));

vi.mock("@/features/public/hooks/usePublicData", () => ({
  useServices: () => ({
    data: {
      content: [{
        id: "service-1",
        name: "Khám răng tổng quát",
        description: null,
        price: 200000,
        imageUrl: null,
        active: true,
      }],
    },
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }),
  useServiceDoctors: (serviceId?: string) => ({
    data: serviceId ? [{
      id: "doctor-1",
      fullName: "Bác sĩ An",
      specialization: "Nha khoa tổng quát",
      workingStatus: "ACTIVE",
    }] : [],
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }),
}));

vi.mock("../hooks/useStaffData", () => ({
  useStaffPatients: (_params: unknown, enabled: boolean) => ({
    data: enabled ? {
      content: staffMocks.patients,
      page: 0,
      size: 20,
      totalElements: staffMocks.patients.length,
      totalPages: 1,
      first: true,
      last: true,
      hasNext: false,
      hasPrevious: false,
    } : undefined,
    isLoading: false,
    isSuccess: enabled,
    error: null,
    refetch: vi.fn(),
  }),
  useCreateStaffPatient: () => ({ mutateAsync: staffMocks.createPatient, isPending: false }),
  useWalkInAvailability: (doctorId?: string, serviceId?: string) => ({
    data: doctorId && serviceId ? {
      doctorId,
      serviceId,
      date: "2026-11-09",
      sessions: [{
        session: "MORNING",
        status: "AVAILABLE",
        sessionStartTime: "08:00:00",
        sessionEndTime: "11:00:00",
        availableCaseCount: 1,
        cases: [
          { queueNumber: 1, estimatedStartTime: "08:00:00", estimatedStartAt: "2026-11-09T08:00:00+07:00", available: false },
          { queueNumber: 2, estimatedStartTime: "09:30:00", estimatedStartAt: "2026-11-09T09:30:00+07:00", available: true },
        ],
      }],
    } : undefined,
    isLoading: false,
    error: null,
    refetch: staffMocks.refetchAvailability,
  }),
  useCreateWalkInAppointment: () => ({ mutateAsync: staffMocks.createWalkIn, isPending: false }),
}));

const existingPatient = {
  id: "patient-1",
  fullName: "Nguyễn Văn A",
  phone: "0901234567",
  dateOfBirth: null,
  gender: "UNSPECIFIED",
  address: null,
  medicalHistory: null,
  allergies: null,
  createdAt: "2026-11-09T00:00:00Z",
  updatedAt: "2026-11-09T00:00:00Z",
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/staff/walk-in"]}>
      <Routes>
        <Route path="/staff/walk-in" element={<StaffWalkInPage />} />
        <Route path="/staff/appointments" element={<p>Trang lịch toàn phòng khám</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function selectAvailableWalkInCase(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Tìm bệnh nhân"), "0901");
  await user.click(screen.getByRole("button", { name: /Nguyễn Văn A/ }));
  await user.click(screen.getByRole("button", { name: /Khám răng tổng quát/ }));
  await user.click(screen.getByRole("button", { name: /Bác sĩ An/ }));

  expect(screen.getByRole("button", { name: /Ca 1 dự kiến 08:00 - Đã có bệnh nhân/ })).toBeDisabled();
  const availableCase = screen.getByRole("button", { name: /Ca 2 dự kiến 09:30 - Còn trống/ });
  expect(availableCase).toBeEnabled();
  await user.click(availableCase);
}

describe("StaffWalkInPage", () => {
  beforeEach(() => {
    staffMocks.patients = [existingPatient];
    staffMocks.createPatient.mockReset();
    staffMocks.createWalkIn.mockReset();
    staffMocks.createWalkIn.mockResolvedValue({ id: "appointment-1" });
    staffMocks.refetchAvailability.mockReset();
    staffMocks.refetchAvailability.mockResolvedValue(undefined);
    staffMocks.showToast.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("chỉ cho chọn ca AVAILABLE và tạo WALK_IN bằng dữ liệu Backend", async () => {
    const user = userEvent.setup();
    renderPage();
    await selectAvailableWalkInCase(user);
    await user.click(screen.getByRole("button", { name: /Xác nhận tạo lịch WALK_IN/ }));

    expect(staffMocks.createWalkIn).toHaveBeenCalledWith({
      patientId: "patient-1",
      doctorId: "doctor-1",
      serviceId: "service-1",
      session: "MORNING",
      queueNumber: 2,
    });
    expect(await screen.findByText("Trang lịch toàn phòng khám")).toBeInTheDocument();
  });

  it("tạo hồ sơ bệnh nhân tại quầy mà không yêu cầu tài khoản", async () => {
    staffMocks.patients = [];
    staffMocks.createPatient.mockResolvedValue({ ...existingPatient, id: "patient-new", fullName: "Nguyễn Văn Mới", phone: "0912345678" });
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText("Tìm bệnh nhân"), "Nguyễn Văn Mới");
    await user.click(screen.getByRole("button", { name: "Tạo hồ sơ bệnh nhân mới" }));
    expect(screen.getByLabelText("Họ và tên *")).toHaveValue("Nguyễn Văn Mới");
    await user.type(screen.getByLabelText("Số điện thoại *"), "0912345678");
    await user.click(screen.getByRole("button", { name: "Lưu hồ sơ bệnh nhân" }));

    await waitFor(() => expect(staffMocks.createPatient).toHaveBeenCalledWith({
      fullName: "Nguyễn Văn Mới",
      phone: "0912345678",
      dateOfBirth: undefined,
      gender: "UNSPECIFIED",
      address: undefined,
      medicalHistory: undefined,
      allergies: undefined,
    }));
    expect(await screen.findByText("0912345678")).toBeInTheDocument();
    expect(screen.queryByLabelText("Email")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Mật khẩu")).not.toBeInTheDocument();
  });

  it("tải lại availability và bỏ ca đã chọn khi Backend trả 409", async () => {
    staffMocks.createWalkIn.mockRejectedValue(new ApiError("Ca khám vừa được đặt. Vui lòng chọn ca khác.", "RESOURCE_CONFLICT", 409));
    const user = userEvent.setup();
    renderPage();
    await selectAvailableWalkInCase(user);
    await user.click(screen.getByRole("button", { name: /Xác nhận tạo lịch WALK_IN/ }));

    await waitFor(() => expect(staffMocks.refetchAvailability).toHaveBeenCalledOnce());
    expect(staffMocks.showToast).toHaveBeenCalledWith("Ca khám vừa được đặt. Vui lòng chọn ca khác.", "error");
    expect(screen.queryByText("Xác nhận tiếp nhận WALK_IN")).not.toBeInTheDocument();
  });
});
