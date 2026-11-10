import { z } from "zod";

export const walkInPatientSchema = z.object({
  fullName: z.string().trim().min(2, "Họ tên phải có ít nhất 2 ký tự").max(150, "Họ tên không được vượt quá 150 ký tự"),
  phone: z.string().trim().min(1, "Số điện thoại không được để trống").max(30, "Số điện thoại không được vượt quá 30 ký tự"),
  dateOfBirth: z.string(),
  gender: z.enum(["MALE", "FEMALE", "OTHER", "UNSPECIFIED"]),
  address: z.string(),
  medicalHistory: z.string(),
  allergies: z.string(),
});

export type WalkInPatientFormValues = z.infer<typeof walkInPatientSchema>;
