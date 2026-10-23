import { describe, expect, it } from "vitest";
import { SERVICE_IMAGE_MAX_BYTES, scheduleSchema, serviceSchema } from "./validation";

describe("doctor schedule validation", () => {
  it("chấp nhận lịch sáng trong khung quy định", () => {
    expect(scheduleSchema.safeParse({ dayOfWeek: 1, session: "MORNING", startTime: "08:00", endTime: "11:00" }).success).toBe(true);
  });

  it("từ chối Chủ Nhật và giờ ngoài khung", () => {
    expect(scheduleSchema.safeParse({ dayOfWeek: 7, session: "AFTERNOON", startTime: "12:00", endTime: "18:00" }).success).toBe(false);
  });
});

describe("dental service validation", () => {
  const validService = { name: "Tẩy trắng răng", description: "", price: 500000, imageUrl: "", imageFile: null };

  it("chấp nhận URL ảnh HTTP/HTTPS hoặc để trống", () => {
    expect(serviceSchema.safeParse(validService).success).toBe(true);
    expect(serviceSchema.safeParse({ ...validService, imageUrl: "https://cdn.example.com/services/whitening.webp" }).success).toBe(true);
  });

  it("từ chối URL ảnh sai giao thức hoặc chứa khoảng trắng", () => {
    expect(serviceSchema.safeParse({ ...validService, imageUrl: "ftp://example.com/image.jpg" }).success).toBe(false);
    expect(serviceSchema.safeParse({ ...validService, imageUrl: "https://example.com/image bad.jpg" }).success).toBe(false);
  });

  it("chấp nhận file ảnh JPEG, PNG hoặc WebP hợp lệ", () => {
    const image = new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], "service.png", { type: "image/png" });
    expect(serviceSchema.safeParse({ ...validService, imageFile: image }).success).toBe(true);
  });

  it("từ chối file không phải ảnh hoặc vượt quá 5 MB", () => {
    const textFile = new File(["not an image"], "service.txt", { type: "text/plain" });
    const oversizedImage = new File([new Uint8Array(SERVICE_IMAGE_MAX_BYTES + 1)], "large.jpg", { type: "image/jpeg" });

    expect(serviceSchema.safeParse({ ...validService, imageFile: textFile }).success).toBe(false);
    expect(serviceSchema.safeParse({ ...validService, imageFile: oversizedImage }).success).toBe(false);
  });
});
