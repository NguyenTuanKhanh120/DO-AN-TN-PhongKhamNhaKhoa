import type { ApiResponse, PageResponse, SearchPageParams } from "@/types/api";
import type {
  AdminDoctor,
  AdminUser,
  Appointment,
  AppointmentHistory,
  AppointmentStatus,
  DashboardSummary,
  DentalService,
  DoctorSchedule,
  DoctorStatus,
  Payment,
  PaymentMethod,
  PaymentStatus,
  RevenueSummary,
  Role,
  ScheduleSession,
  UserStatus,
} from "@/types/domain";
import { apiClient, unwrap } from "@/services/api/client";
import { compactParams } from "@/utils/query";

export interface UserParams extends SearchPageParams { role?: Role; status?: UserStatus }
export interface AppointmentParams extends SearchPageParams { status?: AppointmentStatus; patientId?: string; doctorId?: string; serviceId?: string; fromDate?: string; toDate?: string }
export interface PaymentParams extends SearchPageParams { status?: PaymentStatus; fromDate?: string; toDate?: string; sortBy?: string; direction?: "ASC" | "DESC" }
export interface CreateStaffPayload { email: string; password: string; fullName: string }
export interface CreateDoctorPayload extends CreateStaffPayload { specialization: string; licenseNumber: string; bio: string | null; experienceYears: number }
export interface UpdateDoctorPayload { fullName: string; specialization: string; licenseNumber: string; bio: string | null; rating: number; experienceYears: number }
export interface ServicePayload { name: string; description: string | null; price: number; imageUrl: string | null }
export interface ServiceImageUploadResponse { imageUrl: string }
export interface SchedulePayload { dayOfWeek: number; session: ScheduleSession; startTime: string; endTime: string }

export const getUsers = async (params: UserParams = {}): Promise<PageResponse<AdminUser>> => unwrap((await apiClient.get<ApiResponse<PageResponse<AdminUser>>>("/api/admin/users", { params: compactParams(params) })).data);
export const getUser = async (id: string): Promise<AdminUser> => unwrap((await apiClient.get<ApiResponse<AdminUser>>(`/api/admin/users/${id}`)).data);
export const updateUserStatus = async (id: string, status: UserStatus): Promise<AdminUser> => unwrap((await apiClient.patch<ApiResponse<AdminUser>>(`/api/admin/users/${id}/status`, { status })).data);

export const getStaff = async (params: Omit<UserParams, "role"> = {}): Promise<PageResponse<AdminUser>> => unwrap((await apiClient.get<ApiResponse<PageResponse<AdminUser>>>("/api/admin/staff", { params: compactParams(params) })).data);
export const getStaffMember = async (id: string): Promise<AdminUser> => unwrap((await apiClient.get<ApiResponse<AdminUser>>(`/api/admin/staff/${id}`)).data);
export const createStaff = async (payload: CreateStaffPayload): Promise<AdminUser> => unwrap((await apiClient.post<ApiResponse<AdminUser>>("/api/admin/staff", payload)).data);
export const updateStaffStatus = async (id: string, status: UserStatus): Promise<AdminUser> => unwrap((await apiClient.patch<ApiResponse<AdminUser>>(`/api/admin/staff/${id}/status`, { status })).data);

export const createDoctor = async (payload: CreateDoctorPayload): Promise<AdminDoctor> => unwrap((await apiClient.post<ApiResponse<AdminDoctor>>("/api/admin/doctors", payload)).data);
export const updateDoctor = async (id: string, payload: UpdateDoctorPayload): Promise<AdminDoctor> => unwrap((await apiClient.put<ApiResponse<AdminDoctor>>(`/api/admin/doctors/${id}`, payload)).data);
export const updateDoctorStatus = async (id: string, status: DoctorStatus): Promise<AdminDoctor> => unwrap((await apiClient.patch<ApiResponse<AdminDoctor>>(`/api/admin/doctors/${id}/status`, { status })).data);
export const replaceDoctorServices = async (id: string, serviceIds: string[]): Promise<DentalService[]> => unwrap((await apiClient.put<ApiResponse<DentalService[]>>(`/api/admin/doctors/${id}/services`, { serviceIds })).data);

export const getDoctorSchedules = async (id: string): Promise<DoctorSchedule[]> => unwrap((await apiClient.get<ApiResponse<DoctorSchedule[]>>(`/api/admin/doctors/${id}/schedules`)).data);
export const createDoctorSchedule = async (id: string, payload: SchedulePayload): Promise<DoctorSchedule> => unwrap((await apiClient.post<ApiResponse<DoctorSchedule>>(`/api/admin/doctors/${id}/schedules`, payload)).data);
export const updateDoctorSchedule = async (doctorId: string, scheduleId: string, payload: SchedulePayload): Promise<DoctorSchedule> => unwrap((await apiClient.put<ApiResponse<DoctorSchedule>>(`/api/admin/doctors/${doctorId}/schedules/${scheduleId}`, payload)).data);
export const deleteDoctorSchedule = async (doctorId: string, scheduleId: string): Promise<void> => { await apiClient.delete(`/api/admin/doctors/${doctorId}/schedules/${scheduleId}`); };

export const createService = async (payload: ServicePayload): Promise<DentalService> => unwrap((await apiClient.post<ApiResponse<DentalService>>("/api/admin/services", payload)).data);
export const updateService = async (id: string, payload: ServicePayload): Promise<DentalService> => unwrap((await apiClient.put<ApiResponse<DentalService>>(`/api/admin/services/${id}`, payload)).data);
export const updateServiceStatus = async (id: string, active: boolean): Promise<DentalService> => unwrap((await apiClient.patch<ApiResponse<DentalService>>(`/api/admin/services/${id}/status`, { active })).data);
export const uploadServiceImage = async (file: File): Promise<ServiceImageUploadResponse> => {
  const body = new FormData();
  body.append("file", file);
  return unwrap((await apiClient.post<ApiResponse<ServiceImageUploadResponse>>("/api/admin/services/images", body, {
    headers: { "Content-Type": "multipart/form-data" },
  })).data);
};

export const getAppointments = async (params: AppointmentParams = {}): Promise<PageResponse<Appointment>> => unwrap((await apiClient.get<ApiResponse<PageResponse<Appointment>>>("/api/admin/appointments", { params: compactParams(params) })).data);
export const getAppointmentHistory = async (id: string): Promise<AppointmentHistory[]> => unwrap((await apiClient.get<ApiResponse<AppointmentHistory[]>>(`/api/admin/appointments/${id}/history`)).data);

export const getPayments = async (params: PaymentParams = {}): Promise<PageResponse<Payment>> => unwrap((await apiClient.get<ApiResponse<PageResponse<Payment>>>("/api/admin/payments", { params: compactParams(params) })).data);
export const getPayment = async (id: string): Promise<Payment> => unwrap((await apiClient.get<ApiResponse<Payment>>(`/api/admin/payments/${id}`)).data);
export const confirmPayment = async (id: string, paymentMethod: PaymentMethod): Promise<Payment> => unwrap((await apiClient.patch<ApiResponse<Payment>>(`/api/admin/payments/${id}/confirm`, { paymentMethod })).data);

export const getDashboardSummary = async (): Promise<DashboardSummary> => unwrap((await apiClient.get<ApiResponse<DashboardSummary>>("/api/admin/dashboard/summary")).data);
export const getRevenue = async (params: { fromDate?: string; toDate?: string; month?: string }): Promise<RevenueSummary> => unwrap((await apiClient.get<ApiResponse<RevenueSummary>>("/api/admin/dashboard/revenue", { params: compactParams(params) })).data);
