import { z } from "zod";
import type { DentalService, Doctor } from "@/types/domain";
import type {
  ChatMessageMetadata,
  ChatStreamCompletedEvent,
  ChatStreamErrorEvent,
  ChatStreamStartedEvent,
  ChatStreamTokenEvent,
} from "../types/chatbot";

const chatIntentSchema = z.enum([
  "SYMPTOM_CONSULTATION",
  "SERVICE_CATALOG",
  "SERVICE_RECOMMENDATION",
  "DOCTOR_RECOMMENDATION",
  "DOCTOR_AVAILABLE_SLOTS",
  "SERVICE_AVAILABLE_DOCTORS",
  "AVAILABLE_SLOTS",
  "CLINIC_INFORMATION",
  "APPOINTMENT_GUIDANCE",
  "OUT_OF_SCOPE",
]);

const dentalServiceSchema: z.ZodType<DentalService> = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  price: z.number(),
  imageUrl: z.string().nullable(),
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

const doctorSchema: z.ZodType<Doctor> = z.object({
  id: z.string(),
  fullName: z.string(),
  specialization: z.string(),
  licenseNumber: z.string(),
  bio: z.string().nullable(),
  rating: z.number(),
  experienceYears: z.number(),
  workingStatus: z.enum(["ACTIVE", "INACTIVE", "ON_LEAVE"]),
  createdAt: z.string(),
  updatedAt: z.string(),
});

const sourceSchema = z.object({
  recordId: z.string(),
  title: z.string(),
  url: z.string(),
});

const slotSchema = z.object({
  doctorId: z.string(),
  serviceId: z.string().nullable(),
  date: z.string(),
  session: z.enum(["MORNING", "AFTERNOON"]),
  queueNumber: z.number().int().min(1).max(2),
  sessionStartTime: z.string(),
  sessionEndTime: z.string(),
  estimatedStartTime: z.string(),
  estimatedStartAt: z.string(),
});

export const chatMessageSchema = z.object({
  message: z.string()
    .trim()
    .min(1, "Vui lòng nhập nội dung cần hỏi")
    .max(2000, "Nội dung không được vượt quá 2000 ký tự"),
});

export type ChatMessageFormValues = z.infer<typeof chatMessageSchema>;

export const chatMetadataSchema: z.ZodType<ChatMessageMetadata> = z.object({
  intents: z.array(chatIntentSchema),
  sources: z.array(sourceSchema),
  suggestedServices: z.array(dentalServiceSchema),
  suggestedDoctors: z.array(doctorSchema),
  suggestedSlots: z.array(slotSchema),
  needsClarification: z.boolean(),
});

export const streamStartedSchema: z.ZodType<ChatStreamStartedEvent> = z.object({
  userMessageId: z.string(),
  startedAt: z.string(),
});

export const streamTokenSchema: z.ZodType<ChatStreamTokenEvent> = z.object({
  content: z.string(),
});

export const streamCompletedSchema: z.ZodType<ChatStreamCompletedEvent> = z.object({
  messageId: z.string(),
  createdAt: z.string(),
});

export const streamErrorSchema: z.ZodType<ChatStreamErrorEvent> = z.object({
  code: z.string(),
  message: z.string(),
  partial: z.boolean(),
});

export function normalizeChatMetadata(value: unknown): ChatMessageMetadata {
  const parsed = chatMetadataSchema.safeParse(value);
  return parsed.success
    ? parsed.data
    : {
        intents: [],
        sources: [],
        suggestedServices: [],
        suggestedDoctors: [],
        suggestedSlots: [],
        needsClarification: false,
      };
}
