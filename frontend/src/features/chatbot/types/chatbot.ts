import type { DentalService, Doctor, ScheduleSession } from "@/types/domain";

export type ChatSender = "USER" | "ASSISTANT" | "SYSTEM";

export type ChatIntent =
  | "SYMPTOM_CONSULTATION"
  | "SERVICE_CATALOG"
  | "SERVICE_RECOMMENDATION"
  | "DOCTOR_RECOMMENDATION"
  | "DOCTOR_AVAILABLE_SLOTS"
  | "SERVICE_AVAILABLE_DOCTORS"
  | "AVAILABLE_SLOTS"
  | "CLINIC_INFORMATION"
  | "APPOINTMENT_GUIDANCE"
  | "OUT_OF_SCOPE";

export interface ChatSession {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatSource {
  recordId: string;
  title: string;
  url: string;
}

export interface ChatSlotSuggestion {
  doctorId: string;
  serviceId: string | null;
  date: string;
  session: ScheduleSession;
  startAt: string;
  endAt: string;
}

export interface ChatMessageMetadata {
  intents: ChatIntent[];
  sources: ChatSource[];
  suggestedServices: DentalService[];
  suggestedDoctors: Doctor[];
  suggestedSlots: ChatSlotSuggestion[];
  needsClarification: boolean;
}

export interface ChatMessage {
  id: string;
  sender: ChatSender;
  content: string;
  metadata: ChatMessageMetadata;
  createdAt: string;
}

export interface RawChatMessage extends Omit<ChatMessage, "metadata"> {
  metadata: unknown;
}

export interface ChatStreamStartedEvent {
  userMessageId: string;
  startedAt: string;
}

export interface ChatStreamTokenEvent {
  content: string;
}

export type ChatStreamMetadataEvent = ChatMessageMetadata;

export interface ChatStreamCompletedEvent {
  messageId: string;
  createdAt: string;
}

export interface ChatStreamErrorEvent {
  code: string;
  message: string;
  partial: boolean;
}

export interface ChatStreamHandlers {
  onStarted?: (event: ChatStreamStartedEvent) => void;
  onToken?: (event: ChatStreamTokenEvent) => void;
  onMetadata?: (event: ChatStreamMetadataEvent) => void;
  onCompleted?: (event: ChatStreamCompletedEvent) => void;
}

export interface StreamingChatTurn {
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
}

export const emptyChatMetadata = (): ChatMessageMetadata => ({
  intents: [],
  sources: [],
  suggestedServices: [],
  suggestedDoctors: [],
  suggestedSlots: [],
  needsClarification: false,
});
