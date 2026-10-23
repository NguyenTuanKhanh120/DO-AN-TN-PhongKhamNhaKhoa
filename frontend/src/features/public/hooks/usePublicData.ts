import { useQuery } from "@tanstack/react-query";
import * as service from "../services/public-service";

export const publicKeys = {
  doctors: (params: service.DoctorListParams) => ["public", "doctors", params] as const,
  doctor: (id: string) => ["public", "doctor", id] as const,
  services: (params: service.ServiceListParams) => ["public", "services", params] as const,
  service: (id: string) => ["public", "service", id] as const,
  doctorServices: (id: string) => ["public", "doctor", id, "services"] as const,
  serviceDoctors: (id: string) => ["public", "service", id, "doctors"] as const,
  schedule: (id: string) => ["public", "doctor", id, "schedule"] as const,
  calendar: (doctorId: string, serviceId: string, fromDate?: string, toDate?: string) => ["public", "doctor", doctorId, "calendar", serviceId, fromDate, toDate] as const,
  slots: (doctorId: string, serviceId: string, date: string) => ["public", "doctor", doctorId, "slots", serviceId, date] as const,
};

export const useDoctors = (params: service.DoctorListParams) => useQuery({ queryKey: publicKeys.doctors(params), queryFn: () => service.getDoctors(params) });
export const useDoctor = (id?: string) => useQuery({ queryKey: publicKeys.doctor(id ?? ""), queryFn: () => service.getDoctor(id ?? ""), enabled: Boolean(id) });
export const useServices = (params: service.ServiceListParams) => useQuery({ queryKey: publicKeys.services(params), queryFn: () => service.getServices(params) });
export const useService = (id?: string) => useQuery({ queryKey: publicKeys.service(id ?? ""), queryFn: () => service.getService(id ?? ""), enabled: Boolean(id) });
export const useDoctorServices = (id?: string) => useQuery({ queryKey: publicKeys.doctorServices(id ?? ""), queryFn: () => service.getDoctorServices(id ?? ""), enabled: Boolean(id) });
export const useServiceDoctors = (id?: string) => useQuery({ queryKey: publicKeys.serviceDoctors(id ?? ""), queryFn: () => service.getServiceDoctors(id ?? ""), enabled: Boolean(id) });
export const useDoctorSchedule = (id?: string) => useQuery({ queryKey: publicKeys.schedule(id ?? ""), queryFn: () => service.getDoctorSchedule(id ?? ""), enabled: Boolean(id) });
export const useDoctorCalendar = (doctorId?: string, serviceId?: string, fromDate?: string, toDate?: string) => useQuery({ queryKey: publicKeys.calendar(doctorId ?? "", serviceId ?? "", fromDate, toDate), queryFn: () => service.getDoctorCalendar(doctorId ?? "", serviceId ?? "", fromDate, toDate), enabled: Boolean(doctorId && serviceId) });
export const useAvailableSlots = (doctorId?: string, serviceId?: string, date?: string) => useQuery({ queryKey: publicKeys.slots(doctorId ?? "", serviceId ?? "", date ?? ""), queryFn: () => service.getAvailableSlots(doctorId ?? "", serviceId ?? "", date ?? ""), enabled: Boolean(doctorId && serviceId && date) });
