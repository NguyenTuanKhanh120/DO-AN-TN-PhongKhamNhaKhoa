import type { ApiResponse, PageResponse, SearchPageParams } from "@/types/api";
import type { Appointment, AppointmentStatus, DoctorSchedule, MedicalRecord, Payment, PrescriptionItem } from "@/types/domain";
import { apiClient, unwrap } from "@/services/api/client";
import { compactParams } from "@/utils/query";

export interface DoctorAppointmentParams extends SearchPageParams {
  status?: AppointmentStatus;
  patientId?: string;
  serviceId?: string;
  fromDate?: string;
  toDate?: string;
}

export interface MedicalRecordPayload {
  diagnosis: string;
  treatment: string;
  treatmentPlan: string | null;
  notes: string | null;
  prescription: PrescriptionItem[];
  followUpRequired: boolean;
  recommendedFollowUpDate: string | null;
  followUpNote: string | null;
}

export interface PaymentItemPayload { serviceId: string; quantity: number }

export const getAppointments = async (params: DoctorAppointmentParams = {}): Promise<PageResponse<Appointment>> => {
  const { data } = await apiClient.get<ApiResponse<PageResponse<Appointment>>>("/api/doctor/appointments", { params: compactParams(params) });
  return unwrap(data);
};

export const getTodayAppointments = async (params: DoctorAppointmentParams = {}): Promise<PageResponse<Appointment>> => {
  const { data } = await apiClient.get<ApiResponse<PageResponse<Appointment>>>("/api/doctor/appointments/today", { params: compactParams(params) });
  return unwrap(data);
};

export const startExamination = async (id: string): Promise<{ id: string; status: AppointmentStatus; actualStartAt: string }> => {
  const { data } = await apiClient.patch<ApiResponse<{ id: string; status: AppointmentStatus; actualStartAt: string }>>(`/api/doctor/appointments/${id}/start`);
  return unwrap(data);
};

export const markNoShow = async (id: string): Promise<void> => { await apiClient.patch(`/api/doctor/appointments/${id}/no-show`); };
export const completeExamination = async (id: string): Promise<{ id: string; status: AppointmentStatus; actualEndAt: string }> => {
  const { data } = await apiClient.patch<ApiResponse<{ id: string; status: AppointmentStatus; actualEndAt: string }>>(`/api/doctor/appointments/${id}/complete`);
  return unwrap(data);
};

export const getSchedules = async (): Promise<DoctorSchedule[]> => {
  const { data } = await apiClient.get<ApiResponse<DoctorSchedule[]>>("/api/doctor/schedules");
  return unwrap(data);
};

export const getMedicalRecord = async (id: string): Promise<MedicalRecord> => {
  const { data } = await apiClient.get<ApiResponse<MedicalRecord>>(`/api/doctor/appointments/${id}/medical-record`);
  return unwrap(data);
};

export const createMedicalRecord = async (id: string, payload: MedicalRecordPayload): Promise<MedicalRecord> => {
  const { data } = await apiClient.post<ApiResponse<MedicalRecord>>(`/api/doctor/appointments/${id}/medical-record`, payload);
  return unwrap(data);
};

export const updateMedicalRecord = async (id: string, payload: MedicalRecordPayload): Promise<MedicalRecord> => {
  const { data } = await apiClient.put<ApiResponse<MedicalRecord>>(`/api/doctor/appointments/${id}/medical-record`, payload);
  return unwrap(data);
};

export const getPayment = async (id: string): Promise<Payment> => {
  const { data } = await apiClient.get<ApiResponse<Payment>>(`/api/doctor/appointments/${id}/payment`);
  return unwrap(data);
};

export const updatePaymentItems = async (id: string, items: PaymentItemPayload[]): Promise<Payment> => {
  const { data } = await apiClient.put<ApiResponse<Payment>>(`/api/doctor/appointments/${id}/payment/items`, { items });
  return unwrap(data);
};
