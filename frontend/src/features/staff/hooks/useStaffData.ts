import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as service from "../services/staff-service";

export const staffKeys = {
  appointments: (params: service.StaffAppointmentParams = {}) => ["staff", "appointments", params] as const,
  payments: (params: service.StaffPaymentParams = {}) => ["staff", "payments", params] as const,
  payment: (id: string) => ["staff", "payment", id] as const,
  patients: (params: service.StaffPatientParams = {}) => ["staff", "patients", params] as const,
  walkInAvailability: (doctorId: string, serviceId: string) => ["staff", "walk-in", "availability", doctorId, serviceId] as const,
};

export const useStaffAppointments = (params: service.StaffAppointmentParams = {}) => useQuery({ queryKey: staffKeys.appointments(params), queryFn: () => service.getAppointments(params) });
export const useStaffPayments = (params: service.StaffPaymentParams = {}) => useQuery({ queryKey: staffKeys.payments(params), queryFn: () => service.getPayments(params) });
export const useStaffPayment = (id?: string) => useQuery({ queryKey: staffKeys.payment(id ?? ""), queryFn: () => service.getPayment(id ?? ""), enabled: Boolean(id) });
export const useStaffPatients = (params: service.StaffPatientParams = {}, enabled = true) => useQuery({ queryKey: staffKeys.patients(params), queryFn: () => service.searchPatients(params), enabled });
export const useWalkInAvailability = (doctorId?: string, serviceId?: string) => useQuery({
  queryKey: staffKeys.walkInAvailability(doctorId ?? "", serviceId ?? ""),
  queryFn: () => service.getWalkInAvailability(doctorId ?? "", serviceId ?? ""),
  enabled: Boolean(doctorId && serviceId),
});

export function useConfirmStaffPayment(id: string) {
  const client = useQueryClient();
  return useMutation({ mutationFn: (paymentMethod: "CASH" | "BANK_TRANSFER") => service.confirmPayment(id, paymentMethod), onSuccess: async (data) => {
    client.setQueryData(staffKeys.payment(id), data);
    await client.invalidateQueries({ queryKey: ["staff", "payments"] });
  } });
}

export function useCreateStaffPatient() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: service.createPatient,
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["staff", "patients"] });
    },
  });
}

export function useCreateWalkInAppointment() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: service.createWalkInAppointment,
    onSuccess: async (_, variables) => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ["staff", "appointments"] }),
        client.invalidateQueries({ queryKey: staffKeys.walkInAvailability(variables.doctorId, variables.serviceId) }),
      ]);
    },
  });
}
