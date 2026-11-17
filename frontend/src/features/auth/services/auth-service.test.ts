import { beforeEach, describe, expect, it, vi } from "vitest";
import { registerPatient } from "./auth-service";

const apiMocks = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock("@/services/api/client", () => ({
  apiClient: apiMocks,
  getCsrfHeaders: vi.fn(),
  unwrap: <T,>(response: { data: T }) => response.data,
}));

describe("auth register service", () => {
  beforeEach(() => apiMocks.post.mockReset());

  it("gửi phone dạng string trong payload đăng ký Patient", async () => {
    const payload = {
      fullName: "Nguyễn Văn A",
      phone: "0912345678",
      email: "patient@example.com",
      password: "Patient@123",
    };
    apiMocks.post.mockResolvedValueOnce({
      data: { success: true, code: "SUCCESS", message: "Thành công", data: { role: "PATIENT" } },
    });

    await registerPatient(payload);

    expect(apiMocks.post).toHaveBeenCalledWith("/api/auth/register", payload);
    expect(apiMocks.post.mock.calls[0]?.[1].phone).toBe("0912345678");
  });
});
