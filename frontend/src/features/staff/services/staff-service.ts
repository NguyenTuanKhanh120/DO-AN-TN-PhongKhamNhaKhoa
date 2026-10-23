import type { ApiResponse, PageResponse, SearchPageParams } from "@/types/api";
import type { Appointment, AppointmentStatus, Payment, PaymentMethod, PaymentStatus } from "@/types/domain";
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
