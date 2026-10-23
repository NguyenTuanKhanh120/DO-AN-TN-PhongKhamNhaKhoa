import { z } from "zod";

export const profileSchema = z.object({
  fullName: z.string().trim().min(2, "Họ tên phải có ít nhất 2 ký tự").max(150),
  dateOfBirth: z.string(),
  gender: z.enum(["MALE", "FEMALE", "OTHER", "UNSPECIFIED"]),
  phone: z.string().max(30, "Số điện thoại không được vượt quá 30 ký tự"),
  address: z.string(),
  medicalHistory: z.string(),
  allergies: z.string(),
});

export const reasonSchema = z.object({ reason: z.string().trim().min(1, "Vui lòng nhập lý do").max(100, "Lý do không được vượt quá 100 ký tự") });

export type ProfileFormValues = z.infer<typeof profileSchema>;
export type ReasonFormValues = z.infer<typeof reasonSchema>;
