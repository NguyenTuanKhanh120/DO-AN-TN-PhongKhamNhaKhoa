import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/services/api/errors";
import * as service from "../services/doctor-service";

export const doctorKeys = {
  appointments: (params: service.DoctorAppointmentParams = {}) => ["doctor", "appointments", params] as const,
  today: (params: service.DoctorAppointmentParams = {}) => ["doctor", "appointments", "today", params] as const,
  schedules: ["doctor", "schedules"] as const,
  medicalRecord: (id: string) => ["doctor", "appointment", id, "medical-record"] as const,
  payment: (id: string) => ["doctor", "appointment", id, "payment"] as const,
};

export const useDoctorAppointments = (params: service.DoctorAppointmentParams = {}) => useQuery({ queryKey: doctorKeys.appointments(params), queryFn: () => service.getAppointments(params) });
export const useTodayAppointments = (params: service.DoctorAppointmentParams = {}) => useQuery({ queryKey: doctorKeys.today(params), queryFn: () => service.getTodayAppointments(params) });
export const useDoctorSchedules = () => useQuery({ queryKey: doctorKeys.schedules, queryFn: service.getSchedules });
export const useMedicalRecord = (id?: string, enabled = true) => useQuery({ queryKey: doctorKeys.medicalRecord(id ?? ""), queryFn: () => service.getMedicalRecord(id ?? ""), enabled: Boolean(id) && enabled, retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 1 });
export const useDoctorPayment = (id?: string, enabled = true) => useQuery({ queryKey: doctorKeys.payment(id ?? ""), queryFn: () => service.getPayment(id ?? ""), enabled: Boolean(id) && enabled });

function useAppointmentMutation<TVariables, TResult>(mutationFn: (variables: TVariables) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({ mutationFn, onSuccess: async () => { await client.invalidateQueries({ queryKey: ["doctor", "appointments"] }); } });
}

export const useStartExamination = () => useAppointmentMutation((id: string) => service.startExamination(id));
export const useMarkNoShow = () => useAppointmentMutation((id: string) => service.markNoShow(id));
export const useCompleteExamination = () => useAppointmentMutation((id: string) => service.completeExamination(id));

export function useSaveMedicalRecord(id: string, exists: boolean) {
  const client = useQueryClient();
  return useMutation({ mutationFn: (payload: service.MedicalRecordPayload) => exists ? service.updateMedicalRecord(id, payload) : service.createMedicalRecord(id, payload), onSuccess: (data) => client.setQueryData(doctorKeys.medicalRecord(id), data) });
}

export function useUpdatePaymentItems(id: string) {
  const client = useQueryClient();
  return useMutation({ mutationFn: (items: service.PaymentItemPayload[]) => service.updatePaymentItems(id, items), onSuccess: (data) => client.setQueryData(doctorKeys.payment(id), data) });
}
