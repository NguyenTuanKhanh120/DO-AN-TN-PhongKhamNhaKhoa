import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as service from "../services/patient-service";

export const patientKeys = {
  profile: ["patient", "profile"] as const,
  appointments: (params: service.AppointmentListParams = {}) => ["patient", "appointments", params] as const,
  appointment: (id: string) => ["patient", "appointment", id] as const,
  medicalRecords: (page: number) => ["patient", "medical-records", page] as const,
  medicalRecord: (id: string) => ["patient", "medical-record", id] as const,
  payments: (page: number) => ["patient", "payments", page] as const,
  payment: (id: string) => ["patient", "payment", id] as const,
};

export const usePatientProfile = () => useQuery({ queryKey: patientKeys.profile, queryFn: service.getProfile });
export const usePatientAppointments = (params: service.AppointmentListParams = {}) => useQuery({ queryKey: patientKeys.appointments(params), queryFn: () => service.getAppointments(params) });
export const usePatientAppointment = (id?: string) => useQuery({ queryKey: patientKeys.appointment(id ?? ""), queryFn: () => service.getAppointment(id ?? ""), enabled: Boolean(id) });
export const usePatientMedicalRecords = (page: number) => useQuery({ queryKey: patientKeys.medicalRecords(page), queryFn: () => service.getMedicalRecords(page) });
export const usePatientMedicalRecord = (id?: string) => useQuery({ queryKey: patientKeys.medicalRecord(id ?? ""), queryFn: () => service.getMedicalRecord(id ?? ""), enabled: Boolean(id) });
export const usePatientPayments = (page: number) => useQuery({ queryKey: patientKeys.payments(page), queryFn: () => service.getPayments(page) });
export const usePatientPayment = (id?: string) => useQuery({ queryKey: patientKeys.payment(id ?? ""), queryFn: () => service.getPayment(id ?? ""), enabled: Boolean(id) });

export function useUpdateProfile() {
  const client = useQueryClient();
  return useMutation({ mutationFn: service.updateProfile, onSuccess: (data) => client.setQueryData(patientKeys.profile, data) });
}

export function useBookAppointment() {
  const client = useQueryClient();
  return useMutation({ mutationFn: service.bookAppointment, onSuccess: async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ["patient", "appointments"] }),
      client.invalidateQueries({ queryKey: ["public", "doctor"] }),
    ]);
  } });
}

export function useCancelAppointment() {
  const client = useQueryClient();
  return useMutation({ mutationFn: ({ id, reason }: { id: string; reason: string }) => service.cancelAppointment(id, reason), onSuccess: async (_, variables) => {
    await Promise.all([client.invalidateQueries({ queryKey: ["patient", "appointments"] }), client.invalidateQueries({ queryKey: patientKeys.appointment(variables.id) })]);
  } });
}

export function useRescheduleAppointment() {
  const client = useQueryClient();
  return useMutation({ mutationFn: ({ id, scheduledStartAt, reason }: { id: string; scheduledStartAt: string; reason: string }) => service.rescheduleAppointment(id, scheduledStartAt, reason), onSuccess: async (_, variables) => {
    await Promise.all([client.invalidateQueries({ queryKey: ["patient", "appointments"] }), client.invalidateQueries({ queryKey: patientKeys.appointment(variables.id) }), client.invalidateQueries({ queryKey: ["public", "doctor"] })]);
  } });
}
