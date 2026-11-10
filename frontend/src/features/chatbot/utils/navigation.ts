interface BookingNavigationInput {
  serviceId?: string | null;
  doctorId?: string | null;
  date?: string | null;
  session?: "MORNING" | "AFTERNOON" | null;
  queueNumber?: number | null;
}

export function buildBookingUrl(input: BookingNavigationInput): string {
  const params = new URLSearchParams();
  if (input.serviceId) params.set("serviceId", input.serviceId);
  if (input.doctorId) params.set("doctorId", input.doctorId);
  if (input.date) params.set("date", input.date);
  if (input.session) params.set("session", input.session);
  if (input.queueNumber === 1 || input.queueNumber === 2) params.set("queueNumber", String(input.queueNumber));
  const query = params.toString();
  return query ? `/patient/book-appointment?${query}` : "/patient/book-appointment";
}

export function safeExternalUrl(value: string): string | null {
  try {
    const url = new URL(value);
    return url.protocol === "https:" || url.protocol === "http:" ? url.toString() : null;
  } catch {
    return null;
  }
}
