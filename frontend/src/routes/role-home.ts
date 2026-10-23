import type { Role } from "@/types/domain";

export const roleHome: Record<Role, string> = {
  ADMIN: "/admin/dashboard",
  DOCTOR: "/doctor/dashboard",
  STAFF: "/staff/dashboard",
  PATIENT: "/patient/dashboard",
};
