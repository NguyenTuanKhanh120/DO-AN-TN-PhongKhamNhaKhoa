import type { ApiResponse, PageResponse, SearchPageParams } from "@/types/api";
import type {
  Appointment,
  AppointmentStatus,
  CreateWalkInAppointmentResult,
  Gender,
  Payment,
  PaymentMethod,
  PaymentStatus,
  ScheduleSession,
  StaffPatient,
  WalkInAvailability,
} from "@/types/domain";
import { apiClient, unwrap } from "@/services/api/client";
import { compactParams } from "@/utils/query";

export interface StaffAppointmentParams extends SearchPageParams {
  status?: AppointmentStatus;
  doctorId?: string;
  serviceId?: string;
  fromDate?: string;
  toDate?: string;
  sortBy?: string;
  direction?: "ASC" | "DESC";
}

export interface StaffPaymentParams extends SearchPageParams {
  status?: PaymentStatus;
  fromDate?: string;
  toDate?: string;
  sortBy?: string;
  direction?: "ASC" | "DESC";
}

export type StaffPatientParams = SearchPageParams;

export interface CreateStaffPatientPayload {
  fullName: string;
  phone: string;
  dateOfBirth?: string;
  gender?: Gender;
  address?: string;
  medicalHistory?: string;
  allergies?: string;
}

export interface CreateWalkInAppointmentPayload {
  patientId: string;
  doctorId: string;
  serviceId: string;
  session: ScheduleSession;
  queueNumber: number;
}

export const getAppointments = async (params: StaffAppointmentParams = {}): Promise<PageResponse<Appointment>> => {
  const { data } = await apiClient.get<ApiResponse<PageResponse<Appointment>>>("/api/staff/appointments", { params: compactParams(params) });
  return unwrap(data);
};

export const getPayments = async (params: StaffPaymentParams = {}): Promise<PageResponse<Payment>> => {
  const { data } = await apiClient.get<ApiResponse<PageResponse<Payment>>>("/api/staff/payments", { params: compactParams(params) });
  return unwrap(data);
};

export const getPayment = async (id: string): Promise<Payment> => {
  const { data } = await apiClient.get<ApiResponse<Payment>>(`/api/staff/payments/${id}`);
  return unwrap(data);
};

export const confirmPayment = async (id: string, paymentMethod: PaymentMethod): Promise<Payment> => {
  const { data } = await apiClient.patch<ApiResponse<Payment>>(`/api/staff/payments/${id}/confirm`, { paymentMethod });
  return unwrap(data);
};

export const searchPatients = async (params: StaffPatientParams = {}): Promise<PageResponse<StaffPatient>> => {
  const { data } = await apiClient.get<ApiResponse<PageResponse<StaffPatient>>>("/api/staff/patients", { params: compactParams(params) });
  return unwrap(data);
};

export const createPatient = async (payload: CreateStaffPatientPayload): Promise<StaffPatient> => {
  const { data } = await apiClient.post<ApiResponse<StaffPatient>>("/api/staff/patients", payload);
  return unwrap(data);
};

export const getWalkInAvailability = async (doctorId: string, serviceId: string): Promise<WalkInAvailability> => {
  const { data } = await apiClient.get<ApiResponse<WalkInAvailability>>("/api/staff/appointments/walk-in/availability", {
    params: { doctorId, serviceId },
  });
  return unwrap(data);
};

export const createWalkInAppointment = async (
  payload: CreateWalkInAppointmentPayload,
): Promise<CreateWalkInAppointmentResult> => {
  const { data } = await apiClient.post<ApiResponse<CreateWalkInAppointmentResult>>("/api/staff/appointments/walk-in", payload);
  return unwrap(data);
};
