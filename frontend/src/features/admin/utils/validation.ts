import { z } from "zod";

export const SERVICE_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
export const SERVICE_IMAGE_ACCEPT = "image/jpeg,image/png,image/webp";
const serviceImageTypes = new Set(SERVICE_IMAGE_ACCEPT.split(","));

const accountBase = z.object({
  fullName: z.string().trim().min(2).max(150),
  email: z.string().trim().email("Email không đúng định dạng").max(255),
  password: z.string().min(8, "Mật khẩu phải có ít nhất 8 ký tự").max(72),
});

export const staffSchema = accountBase;
export const doctorCreateSchema = accountBase.extend({ specialization: z.string().trim().min(1).max(150), licenseNumber: z.string().trim().min(1).max(100), bio: z.string().max(5000), experienceYears: z.coerce.number().int().min(0) });
export const doctorUpdateSchema = z.object({ fullName: z.string().trim().min(2).max(150), specialization: z.string().trim().min(1).max(150), licenseNumber: z.string().trim().min(1).max(100), bio: z.string().max(5000), rating: z.coerce.number().min(0).max(5), experienceYears: z.coerce.number().int().min(0) });
export const serviceSchema = z.object({
  name: z.string().trim().min(1).max(200),
  description: z.string(),
  price: z.coerce.number().min(0, "Giá không được âm"),
  imageUrl: z.string().trim().max(1000, "URL ảnh không được vượt quá 1000 ký tự").refine((value) => {
    if (!value) return true;
    if (/\s/.test(value)) return false;
    try {
      const url = new URL(value);
      return (url.protocol === "http:" || url.protocol === "https:") && Boolean(url.hostname);
    } catch {
      return false;
    }
  }, "URL ảnh phải là URL HTTP hoặc HTTPS hợp lệ"),
  imageFile: z.instanceof(File).nullable()
    .refine((file) => !file || file.size > 0, "File ảnh không được để trống")
    .refine((file) => !file || file.size <= SERVICE_IMAGE_MAX_BYTES, "Ảnh không được vượt quá 5 MB")
    .refine((file) => !file || serviceImageTypes.has(file.type), "Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP"),
});
export const scheduleSchema = z.object({ dayOfWeek: z.coerce.number().int().min(1).max(6), session: z.enum(["MORNING", "AFTERNOON"]), startTime: z.string().min(1), endTime: z.string().min(1) }).superRefine((value, context) => {
  const limits = value.session === "MORNING" ? { start: "08:00", end: "11:00" } : { start: "13:00", end: "17:00" };
  if (value.startTime < limits.start || value.endTime > limits.end || value.startTime >= value.endTime) context.addIssue({ code: z.ZodIssueCode.custom, path: ["endTime"], message: `Khung ${value.session === "MORNING" ? "sáng" : "chiều"} phải nằm trong ${limits.start}–${limits.end}` });
});

export type StaffFormValues = z.infer<typeof staffSchema>;
export type DoctorCreateFormValues = z.infer<typeof doctorCreateSchema>;
export type DoctorUpdateFormValues = z.infer<typeof doctorUpdateSchema>;
export type ServiceFormValues = z.infer<typeof serviceSchema>;
export type ScheduleFormValues = z.infer<typeof scheduleSchema>;
