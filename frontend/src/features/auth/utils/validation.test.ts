import { describe, expect, it } from "vitest";
import { loginSchema, registerSchema } from "./validation";

describe("auth validation", () => {
  const validRegistration = {
    fullName: "Nguyễn Văn A",
    phone: "0912345678",
    email: "patient@example.com",
    password: "Patient@123",
    confirmPassword: "Patient@123",
  };

  it("chấp nhận thông tin đăng nhập hợp lệ", () => {
    expect(loginSchema.safeParse({ email: "patient@example.com", password: "Patient@123" }).success).toBe(true);
  });

  it("từ chối email sai và mật khẩu dài hơn Backend cho phép", () => {
    const result = loginSchema.safeParse({ email: "sai-email", password: "x".repeat(73) });
    expect(result.success).toBe(false);
    if (!result.success) expect(result.error.issues.map((issue) => issue.path[0])).toEqual(expect.arrayContaining(["email", "password"]));
  });

  it("từ chối mật khẩu xác nhận không khớp", () => {
    const result = registerSchema.safeParse({ ...validRegistration, confirmPassword: "Patient@456" });
    expect(result.success).toBe(false);
    if (!result.success) expect(result.error.issues[0]?.path).toEqual(["confirmPassword"]);
  });

  it.each([
    ["", "Số điện thoại không được để trống"],
    ["09123abc", "Số điện thoại chỉ được chứa chữ số"],
    ["0912 345 678", "Số điện thoại chỉ được chứa chữ số"],
    ["+84912345678", "Số điện thoại chỉ được chứa chữ số"],
    ["0912-345-678", "Số điện thoại chỉ được chứa chữ số"],
    ["09123456789", "Số điện thoại không được vượt quá 10 số"],
  ])("từ chối số điện thoại '%s'", (phone, message) => {
    const result = registerSchema.safeParse({ ...validRegistration, phone });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues).toEqual(expect.arrayContaining([
        expect.objectContaining({ path: ["phone"], message }),
      ]));
    }
  });

  it("chấp nhận số điện thoại gồm đúng 10 chữ số và giữ kiểu string", () => {
    const result = registerSchema.safeParse(validRegistration);
    expect(result.success).toBe(true);
    if (result.success) expect(result.data.phone).toBe("0912345678");
  });
});
