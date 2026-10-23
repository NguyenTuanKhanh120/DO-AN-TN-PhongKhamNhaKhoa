import type { ApiResponse, PageResponse, SearchPageParams } from "@/types/api";
import type { Appointment, AppointmentStatus, BookAppointmentResult, Gender, PatientMedicalRecord, PatientProfile, Payment } from "@/types/domain";
import { apiClient, unwrap } from "@/services/api/client";
import { compactParams } from "@/utils/query";

export interface AppointmentListParams extends SearchPageParams {
  status?: AppointmentStatus;
  doctorId?: string;
  serviceId?: string;
  fromDate?: string;
  toDate?: string;
}

export interface BookAppointmentPayload {
  doctorId: string;
  serviceId: string;
  scheduledStartAt: string;
  previousAppointmentId: string | null;
}

export interface UpdateProfilePayload {
  fullName: string;
  dateOfBirth: string | null;
  gender: Gender;
  phone: string | null;
  address: string | null;
  medicalHistory: string | null;
  allergies: string | null;
}

export const getProfile = async (): Promise<PatientProfile> => {
  const { data } = await apiClient.get<ApiResponse<PatientProfile>>("/api/patients/me");
  return unwrap(data);
};

export const updateProfile = async (payload: UpdateProfilePayload): Promise<PatientProfile> => {
  const { data } = await apiClient.put<ApiResponse<PatientProfile>>("/api/patients/me", payload);
  return unwrap(data);
};

export const getAppointments = async (params: AppointmentListParams = {}): Promise<PageResponse<Appointment>> => {
  const { data } = await apiClient.get<ApiResponse<PageResponse<Appointment>>>("/api/patient/appointments", { params: compactParams(params) });
  return unwrap(data);
};

export const getAppointment = async (id: string): Promise<Appointment> => {
  const { data } = await apiClient.get<ApiResponse<Appointment>>(`/api/patient/appointments/${id}`);
  return unwrap(data);
};

export const bookAppointment = async (payload: BookAppointmentPayload): Promise<BookAppointmentResult> => {
  const { data } = await apiClient.post<ApiResponse<BookAppointmentResult>>("/api/patient/appointments", payload);
  return unwrap(data);
};

export const cancelAppointment = async (id: string, reason: string): Promise<void> => {
  await apiClient.patch<ApiResponse<unknown>>(`/api/patient/appointments/${id}/cancel`, { reason });
};

export const rescheduleAppointment = async (id: string, scheduledStartAt: string, reason: string): Promise<void> => {
  await apiClient.patch<ApiResponse<unknown>>(`/api/patient/appointments/${id}/reschedule`, { scheduledStartAt, reason });
};

export const getMedicalRecords = async (page = 0, size = 20): Promise<PageResponse<PatientMedicalRecord>> => {
  const { data } = await apiClient.get<ApiResponse<PageResponse<PatientMedicalRecord>>>("/api/patient/medical-records", { params: { page, size } });
  return unwrap(data);
};

export const getMedicalRecord = async (appointmentId: string): Promise<PatientMedicalRecord> => {
  const { data } = await apiClient.get<ApiResponse<PatientMedicalRecord>>(`/api/patient/medical-records/${appointmentId}`);
  return unwrap(data);
};

export const getPayments = async (page = 0, size = 20): Promise<PageResponse<Payment>> => {
  const { data } = await apiClient.get<ApiResponse<PageResponse<Payment>>>("/api/patient/payments", { params: { page, size } });
  return unwrap(data);
};

export const getPayment = async (id: string): Promise<Payment> => {
  const { data } = await apiClient.get<ApiResponse<Payment>>(`/api/patient/payments/${id}`);
  return unwrap(data);
};
