import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Appointment } from "@/types/domain";
import { getAssignedServices, getWeeklyAppointments } from "./doctor-service";

const apiMocks = vi.hoisted(() => ({
  get: vi.fn(),
}));

vi.mock("@/services/api/client", () => ({
  apiClient: apiMocks,
  unwrap: <T,>(response: { data: T }) => response.data,
}));

describe("doctor API service", () => {
  beforeEach(() => {
    apiMocks.get.mockReset();
  });

  it("lấy dịch vụ active được phân công cho bác sĩ hiện tại", async () => {
    const assignedServices = [{
      id: "service-1",
      name: "Cạo vôi răng",
      description: null,
      price: 350000,
      imageUrl: null,
      active: true,
    }];
    apiMocks.get.mockResolvedValueOnce({
      data: {
        success: true,
        code: "SUCCESS",
        message: "Thành công",
        data: assignedServices,
      },
    });

    await expect(getAssignedServices()).resolves.toEqual(assignedServices);
    expect(apiMocks.get).toHaveBeenCalledWith("/api/doctor/services");
  });

  it("lấy đủ các trang lịch khám trong phạm vi tuần của bác sĩ hiện tại", async () => {
    const firstAppointment: Appointment = {
      id: "appointment-1",
      patientId: "patient-1",
      patientFullName: "Nguyễn Văn A",
      patientPhone: "0901234567",
      doctorId: "doctor-1",
      doctorFullName: "Bác sĩ An",
      doctorSpecialization: "Nha khoa tổng quát",
      serviceId: "service-1",
      serviceName: "Trám răng",
      previousAppointmentId: null,
      appointmentDate: "2026-11-09",
      session: "MORNING",
      queueNumber: 1,
      sessionStartTime: "08:00:00",
      sessionEndTime: "11:00:00",
      estimatedStartTime: "08:00:00",
      estimatedStartAt: "2026-11-09T08:00:00+07:00",
      status: "BOOKED",
      checkedInAt: null,
      actualStartAt: null,
      actualEndAt: null,
      cancellationReason: null,
      cancellationSource: null,
      cancelledAt: null,
      createdAt: "2026-11-01T00:00:00Z",
      updatedAt: "2026-11-01T00:00:00Z",
    };
    const secondAppointment = { ...firstAppointment, id: "appointment-2", queueNumber: 2, estimatedStartTime: "09:30:00" };
    apiMocks.get
      .mockResolvedValueOnce({
        data: {
          success: true,
          code: "SUCCESS",
          message: "Thành công",
          data: {
            content: [firstAppointment],
            page: 0,
            size: 100,
            totalElements: 2,
            totalPages: 2,
            first: true,
            last: false,
            hasNext: true,
            hasPrevious: false,
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          success: true,
          code: "SUCCESS",
          message: "Thành công",
          data: {
            content: [secondAppointment],
            page: 1,
            size: 100,
            totalElements: 2,
            totalPages: 2,
            first: false,
            last: true,
            hasNext: false,
            hasPrevious: true,
          },
        },
      });

    await expect(getWeeklyAppointments("2026-11-09", "2026-11-14")).resolves.toEqual([
      firstAppointment,
      secondAppointment,
    ]);
    expect(apiMocks.get).toHaveBeenNthCalledWith(1, "/api/doctor/appointments", {
      params: { fromDate: "2026-11-09", toDate: "2026-11-14", page: 0, size: 100 },
    });
    expect(apiMocks.get).toHaveBeenNthCalledWith(2, "/api/doctor/appointments", {
      params: { fromDate: "2026-11-09", toDate: "2026-11-14", page: 1, size: 100 },
    });
  });
});
