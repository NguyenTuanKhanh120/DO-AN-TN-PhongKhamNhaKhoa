import { z } from "zod";

const prescriptionItemSchema = z.object({
  medicationName: z.string().trim().min(1, "Nhập tên thuốc"),
  dosage: z.string().trim().min(1, "Nhập liều dùng"),
  quantity: z.coerce.number().int().positive("Số lượng phải lớn hơn 0"),
  instructions: z.string().trim().min(1, "Nhập hướng dẫn sử dụng"),
});

export const medicalRecordSchema = z.object({
  diagnosis: z.string().trim().min(1, "Chẩn đoán không được để trống"),
  treatment: z.string().trim().min(1, "Nội dung điều trị không được để trống"),
  treatmentPlan: z.string(),
  notes: z.string(),
  prescription: z.array(prescriptionItemSchema),
  followUpRequired: z.boolean(),
  recommendedFollowUpDate: z.string(),
  followUpNote: z.string(),
}).superRefine((value, context) => {
  if (value.followUpRequired && !value.recommendedFollowUpDate) context.addIssue({ code: z.ZodIssueCode.custom, path: ["recommendedFollowUpDate"], message: "Ngày tái khám là bắt buộc" });
});

export const paymentItemsSchema = z.object({ items: z.array(z.object({ serviceId: z.string().min(1, "Chọn dịch vụ"), quantity: z.coerce.number().int().min(1).max(100) })).min(1, "Cần ít nhất một dịch vụ") });

export type MedicalRecordFormValues = z.infer<typeof medicalRecordSchema>;
export type PaymentItemsFormValues = z.infer<typeof paymentItemsSchema>;
