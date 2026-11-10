import { describe, expect, it } from "vitest";
import { buildBookingUrl, safeExternalUrl } from "./navigation";

describe("chatbot navigation", () => {
  it("tạo URL đặt lịch từ gợi ý slot mà không tự đặt lịch", () => {
    expect(buildBookingUrl({
      serviceId: "service-1",
      doctorId: "doctor-1",
      date: "2026-11-02",
      session: "MORNING",
      queueNumber: 1,
    })).toBe(
      "/patient/book-appointment?serviceId=service-1&doctorId=doctor-1&date=2026-11-02&session=MORNING&queueNumber=1",
    );
  });

  it("chỉ cho phép liên kết nguồn HTTP hoặc HTTPS", () => {
    expect(safeExternalUrl("https://www.nidcr.nih.gov/health-info/tooth-decay"))
      .toBe("https://www.nidcr.nih.gov/health-info/tooth-decay");
    expect(safeExternalUrl("javascript:alert(1)")).toBeNull();
    expect(safeExternalUrl("khong-phai-url")).toBeNull();
  });
});
