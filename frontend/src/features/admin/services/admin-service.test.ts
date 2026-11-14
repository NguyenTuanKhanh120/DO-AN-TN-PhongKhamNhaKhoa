import { beforeEach, describe, expect, it, vi } from "vitest";
import { updateUser, updateUserStatus } from "./admin-service";

const apiMocks = vi.hoisted(() => ({
  put: vi.fn(),
  patch: vi.fn(),
}));

vi.mock("@/services/api/client", () => ({
  apiClient: apiMocks,
  unwrap: <T,>(response: { data: T }) => response.data,
}));

const success = <T,>(data: T) => ({
  data: { success: true, code: "SUCCESS", message: "Thành công", data },
});

describe("admin user API service", () => {
  beforeEach(() => {
    apiMocks.put.mockReset();
    apiMocks.patch.mockReset();
  });

  it("gọi đúng PUT khi sửa họ tên và email", async () => {
    const payload = { fullName: "Nguyễn Văn A", email: "user@example.com" };
    apiMocks.put.mockResolvedValueOnce(success({ id: "user-1", ...payload }));

    await updateUser("user-1", payload);

    expect(apiMocks.put).toHaveBeenCalledWith("/api/admin/users/user-1", payload);
  });

  it("tiếp tục dùng PATCH trạng thái thay vì hard delete", async () => {
    apiMocks.patch.mockResolvedValueOnce(success({ id: "user-1", status: "INACTIVE" }));

    await updateUserStatus("user-1", "INACTIVE");

    expect(apiMocks.patch).toHaveBeenCalledWith(
      "/api/admin/users/user-1/status",
      { status: "INACTIVE" },
    );
  });
});
