import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as service from "../services/staff-service";

export const staffKeys = {
  appointments: (params: service.StaffAppointmentParams = {}) => ["staff", "appointments", params] as const,
  payments: (params: service.StaffPaymentParams = {}) => ["staff", "payments", params] as const,
  payment: (id: string) => ["staff", "payment", id] as const,
};

export const useStaffAppointments = (params: service.StaffAppointmentParams = {}) => useQuery({ queryKey: staffKeys.appointments(params), queryFn: () => service.getAppointments(params) });
export const useStaffPayments = (params: service.StaffPaymentParams = {}) => useQuery({ queryKey: staffKeys.payments(params), queryFn: () => service.getPayments(params) });
export const useStaffPayment = (id?: string) => useQuery({ queryKey: staffKeys.payment(id ?? ""), queryFn: () => service.getPayment(id ?? ""), enabled: Boolean(id) });

export function useConfirmStaffPayment(id: string) {
  const client = useQueryClient();
  return useMutation({ mutationFn: (paymentMethod: "CASH" | "BANK_TRANSFER") => service.confirmPayment(id, paymentMethod), onSuccess: async (data) => {
    client.setQueryData(staffKeys.payment(id), data);
    await client.invalidateQueries({ queryKey: ["staff", "payments"] });
  } });
}
