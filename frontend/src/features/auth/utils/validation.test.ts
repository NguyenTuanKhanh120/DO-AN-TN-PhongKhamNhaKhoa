import { describe, expect, it } from "vitest";
import { loginSchema, registerSchema } from "./validation";

describe("auth validation", () => {
  it("chấp nhận thông tin đăng nhập hợp lệ", () => {
    expect(loginSchema.safeParse({ email: "patient@example.com", password: "Patient@123" }).success).toBe(true);
  });

  it("từ chối email sai và mật khẩu dài hơn Backend cho phép", () => {
    const result = loginSchema.safeParse({ email: "sai-email", password: "x".repeat(73) });
    expect(result.success).toBe(false);
    if (!result.success) expect(result.error.issues.map((issue) => issue.path[0])).toEqual(expect.arrayContaining(["email", "password"]));
  });

  it("từ chối mật khẩu xác nhận không khớp", () => {
    const result = registerSchema.safeParse({ fullName: "Nguyễn Văn A", email: "patient@example.com", password: "Patient@123", confirmPassword: "Patient@456" });
    expect(result.success).toBe(false);
    if (!result.success) expect(result.error.issues[0]?.path).toEqual(["confirmPassword"]);
  });
});
