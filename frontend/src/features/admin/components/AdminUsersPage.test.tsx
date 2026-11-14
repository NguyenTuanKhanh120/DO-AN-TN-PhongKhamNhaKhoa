import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminUser } from "@/types/domain";
import AdminUsersPage from "./AdminUsersPage";

const adminUserMocks = vi.hoisted(() => ({
  users: [] as AdminUser[],
  loading: false,
  error: null as Error | null,
  updateUser: vi.fn(),
  updateStatus: vi.fn(),
  showToast: vi.fn(),
  refetch: vi.fn(),
}));

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: () => ({ user: { id: "admin-current" } }),
}));

vi.mock("@/contexts/ToastContext", () => ({
  useToast: () => ({ showToast: adminUserMocks.showToast }),
}));

vi.mock("../hooks/useAdminData", () => ({
  useAdminUsers: () => ({
    data: adminUserMocks.loading || adminUserMocks.error ? undefined : {
      content: adminUserMocks.users,
      page: 0,
      size: 20,
      totalElements: adminUserMocks.users.length,
      totalPages: adminUserMocks.users.length ? 1 : 0,
      first: true,
      last: true,
      hasNext: false,
      hasPrevious: false,
    },
    isLoading: adminUserMocks.loading,
    error: adminUserMocks.error,
    refetch: adminUserMocks.refetch,
  }),
  useAdminUser: (id?: string) => ({
    data: adminUserMocks.users.find((item) => item.id === id),
    isLoading: false,
    error: null,
    refetch: adminUserMocks.refetch,
  }),
  useUpdateAdminUser: () => ({
    mutateAsync: adminUserMocks.updateUser,
    isPending: false,
  }),
  useUpdateUserStatus: () => ({
    mutateAsync: adminUserMocks.updateStatus,
    isPending: false,
  }),
}));

const staff: AdminUser = {
  id: "staff-1",
  email: "staff@example.com",
  fullName: "Nhân viên A",
  role: "STAFF",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

const doctor: AdminUser = {
  id: "doctor-user-1",
  email: "doctor@example.com",
  fullName: "Bác sĩ A",
  role: "DOCTOR",
  status: "INACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminUsersPage />
    </MemoryRouter>,
  );
}

describe("AdminUsersPage", () => {
  beforeEach(() => {
    adminUserMocks.users = [staff, doctor];
    adminUserMocks.loading = false;
    adminUserMocks.error = null;
    adminUserMocks.updateUser.mockReset();
    adminUserMocks.updateUser.mockResolvedValue(staff);
    adminUserMocks.updateStatus.mockReset();
    adminUserMocks.updateStatus.mockResolvedValue(staff);
    adminUserMocks.showToast.mockReset();
    adminUserMocks.refetch.mockReset();
  });

  afterEach(cleanup);

  it("hiển thị danh sách, chi tiết và đường dẫn quản lý bác sĩ", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByText("Nhân viên A")).toBeInTheDocument();
    expect(screen.getByText("staff@example.com")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Quản lý bác sĩ" })).toHaveAttribute("href", "/admin/doctors");
    expect(screen.queryByText(/Xóa vĩnh viễn/i)).not.toBeInTheDocument();

    const staffRow = screen.getByText("Nhân viên A").closest("tr");
    expect(staffRow).not.toBeNull();
    await user.click(within(staffRow!).getByRole("button", { name: "Xem chi tiết" }));
    expect(screen.getByRole("dialog", { name: "Chi tiết tài khoản" })).toBeInTheDocument();
    expect(screen.getAllByText("staff@example.com")).toHaveLength(2);
  });

  it("chỉ cung cấp lựa chọn tạo STAFF và DOCTOR", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Thêm tài khoản" }));

    expect(screen.getByRole("link", { name: /Thêm nhân viên/ })).toHaveAttribute("href", "/admin/staff");
    expect(screen.getByRole("link", { name: /Thêm bác sĩ/ })).toHaveAttribute("href", "/admin/doctors");
    expect(screen.queryByRole("link", { name: /Thêm Admin/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Thêm Patient/i })).not.toBeInTheDocument();
  });

  it("validate email và gọi mutation PUT với dữ liệu đã sửa", async () => {
    const user = userEvent.setup();
    renderPage();
    const staffRow = screen.getByText("Nhân viên A").closest("tr");

    await user.click(within(staffRow!).getByRole("button", { name: "Sửa" }));
    const editDialog = screen.getByRole("dialog", { name: "Sửa thông tin tài khoản" });
    expect(within(editDialog).getByLabelText("Vai trò")).toBeDisabled();
    expect(within(editDialog).getByLabelText("Trạng thái")).toBeDisabled();

    const emailInput = within(editDialog).getByLabelText(/Email/);
    await user.clear(emailInput);
    await user.type(emailInput, "email-sai");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    expect(await screen.findByText("Email không đúng định dạng")).toBeInTheDocument();
    expect(adminUserMocks.updateUser).not.toHaveBeenCalled();

    await user.clear(emailInput);
    await user.type(emailInput, "staff.updated@example.com");
    const fullNameInput = within(editDialog).getByLabelText(/Họ và tên/);
    await user.clear(fullNameInput);
    await user.type(fullNameInput, "Nhân viên cập nhật");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    await waitFor(() => {
      expect(adminUserMocks.updateUser).toHaveBeenCalledWith({
        id: "staff-1",
        payload: {
          fullName: "Nhân viên cập nhật",
          email: "staff.updated@example.com",
        },
      });
    });
  });

  it("xác nhận vô hiệu hóa và kích hoạt lại tài khoản", async () => {
    const user = userEvent.setup();
    renderPage();

    const staffRow = screen.getByText("Nhân viên A").closest("tr");
    await user.click(within(staffRow!).getByRole("button", { name: "Vô hiệu hóa" }));
    expect(screen.getByText(/tất cả phiên đăng nhập hiện tại sẽ bị thu hồi/i)).toBeInTheDocument();
    const deactivateDialog = screen.getByRole("dialog", { name: "Vô hiệu hóa tài khoản" });
    await user.click(within(deactivateDialog).getByRole("button", { name: "Vô hiệu hóa" }));
    await waitFor(() => {
      expect(adminUserMocks.updateStatus).toHaveBeenCalledWith({ id: "staff-1", status: "INACTIVE" });
    });

    const doctorRow = screen.getByText("Bác sĩ A").closest("tr");
    await user.click(within(doctorRow!).getByRole("button", { name: "Kích hoạt" }));
    const activateDialog = screen.getByRole("dialog", { name: "Kích hoạt tài khoản" });
    await user.click(within(activateDialog).getByRole("button", { name: "Kích hoạt" }));
    await waitFor(() => {
      expect(adminUserMocks.updateStatus).toHaveBeenCalledWith({ id: "doctor-user-1", status: "ACTIVE" });
    });
  });

  it("hiển thị loading, error và empty state", () => {
    adminUserMocks.loading = true;
    const loadingView = renderPage();
    expect(screen.getByLabelText("Đang tải")).toBeInTheDocument();
    loadingView.unmount();

    adminUserMocks.loading = false;
    adminUserMocks.error = new Error("Lỗi tải dữ liệu");
    const errorView = renderPage();
    expect(screen.getByText("Không thể tải dữ liệu")).toBeInTheDocument();
    errorView.unmount();

    adminUserMocks.error = null;
    adminUserMocks.users = [];
    renderPage();
    expect(screen.getByText("Chưa có dữ liệu")).toBeInTheDocument();
  });
});
