import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  createPatient,
  createWalkInAppointment,
  getWalkInAvailability,
  searchPatients,
} from "./staff-service";

const apiMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@/services/api/client", () => ({
  apiClient: apiMocks,
  unwrap: <T,>(response: { data: T }) => response.data,
}));

const success = <T,>(data: T) => ({ data: { success: true, code: "SUCCESS", message: "Thành công", data } });

describe("staff walk-in API service", () => {
  beforeEach(() => {
    apiMocks.get.mockReset();
    apiMocks.post.mockReset();
  });

  it("gọi đúng các endpoint tìm/tạo Patient và availability/create WALK_IN", async () => {
    const patient = { id: "patient-1" };
    const page = { content: [patient] };
    const availability = { doctorId: "doctor-1", serviceId: "service-1", sessions: [] };
    const appointment = { id: "appointment-1" };
    apiMocks.get
      .mockResolvedValueOnce(success(page))
      .mockResolvedValueOnce(success(availability));
    apiMocks.post
      .mockResolvedValueOnce(success(patient))
      .mockResolvedValueOnce(success(appointment));

    await searchPatients({ search: "0901", page: 0, size: 20 });
    expect(apiMocks.get).toHaveBeenNthCalledWith(1, "/api/staff/patients", {
      params: { search: "0901", page: 0, size: 20 },
    });

    const patientPayload = { fullName: "Nguyễn Văn A", phone: "0901234567" };
    await createPatient(patientPayload);
    expect(apiMocks.post).toHaveBeenNthCalledWith(1, "/api/staff/patients", patientPayload);

    await getWalkInAvailability("doctor-1", "service-1");
    expect(apiMocks.get).toHaveBeenNthCalledWith(2, "/api/staff/appointments/walk-in/availability", {
      params: { doctorId: "doctor-1", serviceId: "service-1" },
    });

    const appointmentPayload = {
      patientId: "patient-1",
      doctorId: "doctor-1",
      serviceId: "service-1",
      session: "MORNING" as const,
      queueNumber: 2,
    };
    await createWalkInAppointment(appointmentPayload);
    expect(apiMocks.post).toHaveBeenNthCalledWith(2, "/api/staff/appointments/walk-in", appointmentPayload);
  });
});
