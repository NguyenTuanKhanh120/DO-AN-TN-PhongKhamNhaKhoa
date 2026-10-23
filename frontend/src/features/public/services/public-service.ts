import type { ApiResponse, PageResponse, SearchPageParams } from "@/types/api";
import type { AvailableSlot, DentalService, Doctor, DoctorCalendar, DoctorSchedule, DoctorStatus } from "@/types/domain";
import { apiClient, unwrap } from "@/services/api/client";
import { compactParams } from "@/utils/query";

export interface DoctorListParams extends SearchPageParams {
  specialization?: string;
  workingStatus?: DoctorStatus;
}

export interface ServiceListParams extends SearchPageParams {
  active?: boolean;
}

export async function getDoctors(params: DoctorListParams = {}): Promise<PageResponse<Doctor>> {
  const { data } = await apiClient.get<ApiResponse<PageResponse<Doctor>>>("/api/doctors", { params: compactParams(params) });
  return unwrap(data);
}

export async function getDoctor(id: string): Promise<Doctor> {
  const { data } = await apiClient.get<ApiResponse<Doctor>>(`/api/doctors/${id}`);
  return unwrap(data);
}

export async function getServices(params: ServiceListParams = {}): Promise<PageResponse<DentalService>> {
  const { data } = await apiClient.get<ApiResponse<PageResponse<DentalService>>>("/api/services", { params: compactParams(params) });
  return unwrap(data);
}

export async function getService(id: string): Promise<DentalService> {
  const { data } = await apiClient.get<ApiResponse<DentalService>>(`/api/services/${id}`);
  return unwrap(data);
}

export async function getDoctorServices(doctorId: string): Promise<DentalService[]> {
  const { data } = await apiClient.get<ApiResponse<DentalService[]>>(`/api/doctors/${doctorId}/services`);
  return unwrap(data);
}

export async function getServiceDoctors(serviceId: string): Promise<Doctor[]> {
  const { data } = await apiClient.get<ApiResponse<Doctor[]>>(`/api/services/${serviceId}/doctors`);
  return unwrap(data);
}

export async function getDoctorSchedule(doctorId: string): Promise<DoctorSchedule[]> {
  const { data } = await apiClient.get<ApiResponse<DoctorSchedule[]>>(`/api/doctors/${doctorId}/schedule`);
  return unwrap(data);
}

export async function getDoctorCalendar(doctorId: string, serviceId: string, fromDate?: string, toDate?: string): Promise<DoctorCalendar> {
  const { data } = await apiClient.get<ApiResponse<DoctorCalendar>>(`/api/doctors/${doctorId}/calendar`, { params: compactParams({ serviceId, fromDate, toDate }) });
  return unwrap(data);
}

export async function getAvailableSlots(doctorId: string, serviceId: string, date: string): Promise<AvailableSlot[]> {
  const { data } = await apiClient.get<ApiResponse<AvailableSlot[]>>(`/api/doctors/${doctorId}/available-slots`, { params: { serviceId, date } });
  return unwrap(data);
}
