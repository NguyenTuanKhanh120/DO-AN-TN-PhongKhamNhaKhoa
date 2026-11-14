import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/services/api/errors";
import type { StaffPatient } from "@/types/domain";
import StaffWalkInPage from "./StaffWalkInPage";

const staffMocks = vi.hoisted(() => ({
  patientPages: [] as StaffPatient[][],
  patientLoading: false,
  patientError: null as Error | null,
  useStaffPatients: vi.fn(),
  refetchPatients: vi.fn(),
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
  useStaffPatients: (params: { search?: string; page?: number; size?: number }, enabled = true) => {
    staffMocks.useStaffPatients(params, enabled);
    const page = params.page ?? 0;
    const allPatients = staffMocks.patientPages.flat();
    const normalizedSearch = params.search?.trim().toLocaleLowerCase("vi-VN");
    const matchingPatients = normalizedSearch
      ? allPatients.filter((patient) => [patient.fullName, patient.phone, patient.id]
          .some((value) => String(value ?? "").toLocaleLowerCase("vi-VN").includes(normalizedSearch)))
      : null;
    const content = matchingPatients ?? staffMocks.patientPages[page] ?? [];
    const totalElements = matchingPatients?.length ?? allPatients.length;
    const totalPages = matchingPatients ? (matchingPatients.length ? 1 : 0) : staffMocks.patientPages.length;
    return {
      data: enabled && !staffMocks.patientLoading && !staffMocks.patientError ? {
        content,
        page,
        size: params.size ?? 20,
        totalElements,
        totalPages,
        first: page === 0,
        last: totalPages === 0 || page >= totalPages - 1,
        hasNext: page < totalPages - 1,
        hasPrevious: page > 0,
      } : undefined,
      isLoading: enabled && staffMocks.patientLoading,
      isSuccess: enabled && !staffMocks.patientLoading && !staffMocks.patientError,
      error: enabled ? staffMocks.patientError : null,
      refetch: staffMocks.refetchPatients,
    };
  },
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

const existingPatient: StaffPatient = {
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

const secondPatient: StaffPatient = {
  ...existingPatient,
  id: "patient-2",
  fullName: "Trần Thị Mai",
  phone: "0934567890",
  dateOfBirth: "2001-11-08",
  gender: "FEMALE",
  address: "12 Nguyễn Trãi, Hà Nội",
  medicalHistory: "Tăng huyết áp",
  allergies: "Dị ứng Penicillin",
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
  await user.click(screen.getByRole("button", { name: "Chọn Nguyễn Văn A" }));
  await user.click(screen.getByRole("button", { name: /Khám răng tổng quát/ }));
  await user.click(screen.getByRole("button", { name: /Bác sĩ An/ }));

  expect(screen.getByRole("button", { name: /Ca 1 dự kiến 08:00 - Đã có bệnh nhân/ })).toBeDisabled();
  const availableCase = screen.getByRole("button", { name: /Ca 2 dự kiến 09:30 - Còn trống/ });
  expect(availableCase).toBeEnabled();
  await user.click(availableCase);
}

describe("StaffWalkInPage", () => {
  beforeEach(() => {
    staffMocks.patientPages = [[existingPatient]];
    staffMocks.patientLoading = false;
    staffMocks.patientError = null;
    staffMocks.useStaffPatients.mockReset();
    staffMocks.refetchPatients.mockReset();
    staffMocks.refetchPatients.mockResolvedValue(undefined);
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

  it("tải trang Patient đầu tiên ngay khi mở và luôn hiển thị search cùng nút tạo hồ sơ", () => {
    renderPage();

    expect(staffMocks.useStaffPatients).toHaveBeenCalledWith({
      search: undefined,
      page: 0,
      size: 20,
    }, true);
    expect(screen.getByText("Nguyễn Văn A")).toBeInTheDocument();
    expect(screen.queryByText(/Nhập ít nhất 2 ký tự/)).not.toBeInTheDocument();

    const actions = screen.getByTestId("patient-search-actions");
    expect(within(actions).getByLabelText("Tìm bệnh nhân")).toBeInTheDocument();
    expect(within(actions).getByRole("button", { name: "Tạo hồ sơ bệnh nhân mới" })).toBeInTheDocument();
  });

  it("lọc danh sách theo search và tải lại danh sách mặc định khi xóa search", async () => {
    staffMocks.patientPages = [[existingPatient, secondPatient]];
    const user = userEvent.setup();
    renderPage();

    const search = screen.getByLabelText("Tìm bệnh nhân");
    await user.type(search, "Trần");
    expect(screen.getByText("Trần Thị Mai")).toBeInTheDocument();
    expect(screen.queryByText("Nguyễn Văn A")).not.toBeInTheDocument();
    expect(staffMocks.useStaffPatients).toHaveBeenLastCalledWith({
      search: "Trần",
      page: 0,
      size: 20,
    }, true);

    await user.clear(search);
    expect(screen.getByText("Nguyễn Văn A")).toBeInTheDocument();
    expect(screen.getByText("Trần Thị Mai")).toBeInTheDocument();
    expect(staffMocks.useStaffPatients).toHaveBeenLastCalledWith({
      search: undefined,
      page: 0,
      size: 20,
    }, true);
  });

  it("hiển thị ngày sinh khi có và không render giá trị null", () => {
    staffMocks.patientPages = [[existingPatient, secondPatient]];
    renderPage();

    expect(screen.getByText(/2001/)).toBeInTheDocument();
    expect(screen.queryByText("null")).not.toBeInTheDocument();
  });

  it("mở chi tiết hồ sơ khi nhấn vào bệnh nhân và có thể chọn sau khi kiểm tra", async () => {
    staffMocks.patientPages = [[secondPatient]];
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Xem chi tiết hồ sơ Trần Thị Mai" }));

    const dialog = screen.getByRole("dialog", { name: "Chi tiết hồ sơ bệnh nhân" });
    expect(within(dialog).getByText("Trần Thị Mai")).toBeInTheDocument();
    expect(within(dialog).getByText("0934567890")).toBeInTheDocument();
    expect(within(dialog).getByText(/2001/)).toBeInTheDocument();
    expect(within(dialog).getByText("Nữ")).toBeInTheDocument();
    expect(within(dialog).getByText("12 Nguyễn Trãi, Hà Nội")).toBeInTheDocument();
    expect(within(dialog).getByText("Tăng huyết áp")).toBeInTheDocument();
    expect(within(dialog).getByText("Dị ứng Penicillin")).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Chọn bệnh nhân này" }));
    expect(screen.queryByRole("dialog", { name: "Chi tiết hồ sơ bệnh nhân" })).not.toBeInTheDocument();
    expect(screen.getByText("2. Chọn dịch vụ")).toBeInTheDocument();
  });

  it("giữ nút tạo hồ sơ khi search không có kết quả", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText("Tìm bệnh nhân"), "Nguyễn XYZ");
    expect(screen.getByText("Không tìm thấy hồ sơ bệnh nhân phù hợp")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tạo hồ sơ bệnh nhân mới" })).toBeInTheDocument();
  });

  it("hiển thị empty database và vẫn mở được modal tạo hồ sơ", async () => {
    staffMocks.patientPages = [];
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByText("Chưa có hồ sơ bệnh nhân")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tạo hồ sơ bệnh nhân mới" }));
    expect(screen.getByRole("heading", { name: "Tạo hồ sơ bệnh nhân tại quầy" })).toBeInTheDocument();
  });

  it("chọn và đổi bệnh nhân mà không cần nhập lại search", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Chọn Nguyễn Văn A" }));
    expect(screen.getByText("2. Chọn dịch vụ")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Đổi bệnh nhân" }));
    expect(screen.getByText("Danh sách hồ sơ bệnh nhân")).toBeInTheDocument();
    expect(screen.getByText("Nguyễn Văn A")).toBeInTheDocument();
  });

  it("phân trang bằng Backend và reset về trang 0 khi search thay đổi", async () => {
    staffMocks.patientPages = [[existingPatient], [secondPatient]];
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Trang sau" }));
    expect(screen.getByText("Trần Thị Mai")).toBeInTheDocument();
    expect(staffMocks.useStaffPatients).toHaveBeenLastCalledWith({
      search: undefined,
      page: 1,
      size: 20,
    }, true);

    await user.type(screen.getByLabelText("Tìm bệnh nhân"), "Nguyễn");
    expect(screen.getByText("Nguyễn Văn A")).toBeInTheDocument();
    expect(staffMocks.useStaffPatients).toHaveBeenLastCalledWith({
      search: "Nguyễn",
      page: 0,
      size: 20,
    }, true);
  });

  it("hiển thị loading mà không làm mất nút tạo hồ sơ", () => {
    staffMocks.patientLoading = true;
    renderPage();

    expect(screen.getByLabelText("Đang tải")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tạo hồ sơ bệnh nhân mới" })).toBeInTheDocument();
  });

  it("hiển thị lỗi, cho thử lại và không làm mất nút tạo hồ sơ", async () => {
    staffMocks.patientError = new ApiError("Không thể tải danh sách bệnh nhân.", "INTERNAL_ERROR", 500);
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByText("Không thể tải dữ liệu")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tạo hồ sơ bệnh nhân mới" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(staffMocks.refetchPatients).toHaveBeenCalledOnce();
  });

  it("chỉ cho chọn ca AVAILABLE và tạo lịch bằng dữ liệu Backend", async () => {
    const user = userEvent.setup();
    renderPage();
    await selectAvailableWalkInCase(user);

    expect(screen.getByRole("heading", { name: "Xác nhận tiếp nhận" })).toBeInTheDocument();
    expect(screen.queryByText(/WALK_IN/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Xác nhận tạo lịch" }));

    expect(staffMocks.createWalkIn).toHaveBeenCalledWith({
      patientId: "patient-1",
      doctorId: "doctor-1",
      serviceId: "service-1",
      session: "MORNING",
      queueNumber: 2,
    });
    expect(staffMocks.showToast).toHaveBeenCalledWith("Tạo lịch thành công", "success");
    expect(await screen.findByText("Trang lịch toàn phòng khám")).toBeInTheDocument();
  });

  it("tạo hồ sơ bệnh nhân tại quầy mà không yêu cầu tài khoản", async () => {
    staffMocks.patientPages = [[]];
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
    expect(screen.getByText("2. Chọn dịch vụ")).toBeInTheDocument();
    expect(screen.queryByLabelText("Email")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Mật khẩu")).not.toBeInTheDocument();
  });

  it("tải lại availability và bỏ ca đã chọn khi Backend trả 409", async () => {
    staffMocks.createWalkIn.mockRejectedValue(new ApiError("Ca khám vừa được đặt. Vui lòng chọn ca khác.", "RESOURCE_CONFLICT", 409));
    const user = userEvent.setup();
    renderPage();
    await selectAvailableWalkInCase(user);
    await user.click(screen.getByRole("button", { name: "Xác nhận tạo lịch" }));

    await waitFor(() => expect(staffMocks.refetchAvailability).toHaveBeenCalledOnce());
    expect(staffMocks.showToast).toHaveBeenCalledWith("Ca khám vừa được đặt. Vui lòng chọn ca khác.", "error");
    expect(screen.queryByText("Xác nhận tiếp nhận")).not.toBeInTheDocument();
  });
});
