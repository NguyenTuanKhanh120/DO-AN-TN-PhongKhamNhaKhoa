import { useMemo } from "react";
import { useLocation } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import type { PageResponse } from "@/types/api";
import type { Appointment } from "@/types/domain";

function isAppointment(value: unknown): value is Appointment {
  return typeof value === "object" && value !== null && "id" in value && "status" in value && "scheduledStartAt" in value;
}

export function useCachedDoctorAppointment(id?: string): Appointment | undefined {
  const client = useQueryClient();
  const location = useLocation();
  return useMemo(() => {
    const state: unknown = location.state;
    if (typeof state === "object" && state !== null && "appointment" in state && isAppointment(state.appointment) && state.appointment.id === id) return state.appointment;
    const pages = client.getQueriesData<PageResponse<Appointment>>({ queryKey: ["doctor", "appointments"] });
    for (const [, page] of pages) {
      const appointment = page?.content.find((item) => item.id === id);
      if (appointment) return appointment;
    }
    return undefined;
  }, [client, id, location.state]);
}
