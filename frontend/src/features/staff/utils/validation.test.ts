import { describe, expect, it } from "vitest";
import { walkInPatientSchema } from "./validation";

const validPatient = {
  fullName: "Nguyễn Văn A",
  phone: "0901234567",
  dateOfBirth: "",
  gender: "UNSPECIFIED",
  address: "",
  medicalHistory: "",
  allergies: "",
};

describe("walk-in patient validation", () => {
  it("chấp nhận hồ sơ tại quầy không có email hoặc mật khẩu", () => {
    expect(walkInPatientSchema.safeParse(validPatient).success).toBe(true);
  });

  it("yêu cầu họ tên và số điện thoại hợp lệ", () => {
    expect(walkInPatientSchema.safeParse({ ...validPatient, fullName: "A" }).success).toBe(false);
    expect(walkInPatientSchema.safeParse({ ...validPatient, phone: "" }).success).toBe(false);
  });
});
