import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { publicKeys } from "@/features/public/hooks/usePublicData";
import * as service from "../services/admin-service";

export const adminKeys = {
  users: (params: service.UserParams = {}) => ["admin", "users", params] as const,
  user: (id: string) => ["admin", "user", id] as const,
  staff: (params: Omit<service.UserParams, "role"> = {}) => ["admin", "staff", params] as const,
  staffMember: (id: string) => ["admin", "staff-member", id] as const,
  doctorSchedules: (id: string) => ["admin", "doctor", id, "schedules"] as const,
  appointments: (params: service.AppointmentParams = {}) => ["admin", "appointments", params] as const,
  history: (id: string) => ["admin", "appointment", id, "history"] as const,
  payments: (params: service.PaymentParams = {}) => ["admin", "payments", params] as const,
  payment: (id: string) => ["admin", "payment", id] as const,
  summary: ["admin", "dashboard", "summary"] as const,
  revenue: (params: { fromDate?: string; toDate?: string; month?: string }) => ["admin", "dashboard", "revenue", params] as const,
};

export const useAdminUsers = (params: service.UserParams = {}) => useQuery({ queryKey: adminKeys.users(params), queryFn: () => service.getUsers(params) });
export const useAdminUser = (id?: string) => useQuery({ queryKey: adminKeys.user(id ?? ""), queryFn: () => service.getUser(id ?? ""), enabled: Boolean(id) });
export const useAdminStaff = (params: Omit<service.UserParams, "role"> = {}) => useQuery({ queryKey: adminKeys.staff(params), queryFn: () => service.getStaff(params) });
export const useAdminStaffMember = (id?: string) => useQuery({ queryKey: adminKeys.staffMember(id ?? ""), queryFn: () => service.getStaffMember(id ?? ""), enabled: Boolean(id) });
export const useAdminDoctorSchedules = (id?: string) => useQuery({ queryKey: adminKeys.doctorSchedules(id ?? ""), queryFn: () => service.getDoctorSchedules(id ?? ""), enabled: Boolean(id) });
export const useAdminAppointments = (params: service.AppointmentParams = {}) => useQuery({ queryKey: adminKeys.appointments(params), queryFn: () => service.getAppointments(params) });
export const useAppointmentHistory = (id?: string) => useQuery({ queryKey: adminKeys.history(id ?? ""), queryFn: () => service.getAppointmentHistory(id ?? ""), enabled: Boolean(id) });
export const useAdminPayments = (params: service.PaymentParams = {}) => useQuery({ queryKey: adminKeys.payments(params), queryFn: () => service.getPayments(params) });
export const useAdminPayment = (id?: string) => useQuery({ queryKey: adminKeys.payment(id ?? ""), queryFn: () => service.getPayment(id ?? ""), enabled: Boolean(id) });
export const useDashboardSummary = () => useQuery({ queryKey: adminKeys.summary, queryFn: service.getDashboardSummary });
export const useRevenue = (params: { fromDate?: string; toDate?: string; month?: string }) => useQuery({ queryKey: adminKeys.revenue(params), queryFn: () => service.getRevenue(params) });

function useInvalidateMutation<TVariables, TResult>(mutationFn: (variables: TVariables) => Promise<TResult>, queryKey: readonly unknown[]) {
  const client = useQueryClient();
  return useMutation({ mutationFn, onSuccess: async () => { await client.invalidateQueries({ queryKey }); } });
}

export const useUpdateUserStatus = () => useInvalidateMutation(({ id, status }: { id: string; status: "ACTIVE" | "INACTIVE" }) => service.updateUserStatus(id, status), ["admin", "users"]);
export const useCreateStaff = () => useInvalidateMutation(service.createStaff, ["admin", "staff"]);
export const useUpdateStaffStatus = () => useInvalidateMutation(({ id, status }: { id: string; status: "ACTIVE" | "INACTIVE" }) => service.updateStaffStatus(id, status), ["admin", "staff"]);

export function useCreateDoctor() {
  const client = useQueryClient();
  return useMutation({ mutationFn: service.createDoctor, onSuccess: async () => { await client.invalidateQueries({ queryKey: ["public", "doctors"] }); } });
}
export function useUpdateDoctor(id: string) {
  const client = useQueryClient();
  return useMutation({ mutationFn: (payload: service.UpdateDoctorPayload) => service.updateDoctor(id, payload), onSuccess: async () => { await client.invalidateQueries({ queryKey: publicKeys.doctor(id) }); } });
}
export function useUpdateDoctorStatus(id: string) {
  const client = useQueryClient();
  return useMutation({ mutationFn: (status: "ACTIVE" | "INACTIVE" | "ON_LEAVE") => service.updateDoctorStatus(id, status), onSuccess: async () => { await client.invalidateQueries({ queryKey: publicKeys.doctor(id) }); } });
}
export function useReplaceDoctorServices(id: string) {
  const client = useQueryClient();
  return useMutation({ mutationFn: (serviceIds: string[]) => service.replaceDoctorServices(id, serviceIds), onSuccess: async () => { await client.invalidateQueries({ queryKey: publicKeys.doctorServices(id) }); } });
}
export function useScheduleMutations(id: string) {
  const client = useQueryClient();
  const invalidate = () => client.invalidateQueries({ queryKey: adminKeys.doctorSchedules(id) });
  return {
    create: useMutation({ mutationFn: (payload: service.SchedulePayload) => service.createDoctorSchedule(id, payload), onSuccess: invalidate }),
    update: useMutation({ mutationFn: ({ scheduleId, payload }: { scheduleId: string; payload: service.SchedulePayload }) => service.updateDoctorSchedule(id, scheduleId, payload), onSuccess: invalidate }),
    remove: useMutation({ mutationFn: (scheduleId: string) => service.deleteDoctorSchedule(id, scheduleId), onSuccess: invalidate }),
  };
}

export function useServiceMutations() {
  const client = useQueryClient();
  const invalidate = () => client.invalidateQueries({ queryKey: ["public", "services"] });
  return {
    upload: useMutation({ mutationFn: service.uploadServiceImage }),
    create: useMutation({ mutationFn: service.createService, onSuccess: invalidate }),
    update: useMutation({ mutationFn: ({ id, payload }: { id: string; payload: service.ServicePayload }) => service.updateService(id, payload), onSuccess: invalidate }),
    status: useMutation({ mutationFn: ({ id, active }: { id: string; active: boolean }) => service.updateServiceStatus(id, active), onSuccess: invalidate }),
  };
}

export function useConfirmAdminPayment(id: string) {
  const client = useQueryClient();
  return useMutation({ mutationFn: (method: "CASH" | "BANK_TRANSFER") => service.confirmPayment(id, method), onSuccess: async (data) => { client.setQueryData(adminKeys.payment(id), data); await client.invalidateQueries({ queryKey: ["admin", "payments"] }); } });
}
