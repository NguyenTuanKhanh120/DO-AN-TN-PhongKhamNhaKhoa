import { z } from "zod";

export const loginSchema = z.object({
  email: z.string().trim().min(1, "Email không được để trống").email("Email không đúng định dạng").max(255),
  password: z.string().min(1, "Mật khẩu không được để trống").max(72, "Mật khẩu không được vượt quá 72 ký tự"),
});

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, "Họ tên phải có ít nhất 2 ký tự").max(150),
    email: z.string().trim().min(1, "Email không được để trống").email("Email không đúng định dạng").max(255),
    password: z.string().min(8, "Mật khẩu phải có ít nhất 8 ký tự").max(72),
    confirmPassword: z.string().min(1, "Vui lòng xác nhận mật khẩu"),
  })
  .refine((value) => value.password === value.confirmPassword, {
    path: ["confirmPassword"],
    message: "Mật khẩu xác nhận không khớp",
  });

export const unlockSchema = z.object({
  password: z.string().min(1, "Vui lòng nhập mật khẩu").max(72),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
export type RegisterFormValues = z.infer<typeof registerSchema>;
export type UnlockFormValues = z.infer<typeof unlockSchema>;
