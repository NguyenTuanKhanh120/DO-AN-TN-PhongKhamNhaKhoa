import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import type { ChatMessage as ChatMessageModel } from "../types/chatbot";
import { ChatMessage } from "./ChatMessage";

describe("ChatMessage", () => {
  afterEach(cleanup);

  it("hiển thị dữ liệu dịch vụ, bác sĩ, slot và nguồn do Backend xác minh", () => {
    const message: ChatMessageModel = {
      id: "message-1",
      sender: "ASSISTANT",
      content: "Bạn có thể tham khảo lịch sau.",
      createdAt: "2026-11-02T01:00:00Z",
      metadata: {
        intents: ["DOCTOR_RECOMMENDATION", "AVAILABLE_SLOTS"],
        needsClarification: false,
        sources: [{ recordId: "NIDCR-1", title: "Sâu răng", url: "https://www.nidcr.nih.gov/health-info/tooth-decay" }],
        suggestedServices: [{
          id: "service-1",
          name: "Khám răng tổng quát",
          description: "Kiểm tra sức khỏe răng miệng",
          price: 200000,
          imageUrl: null,
          active: true,
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
        }],
        suggestedDoctors: [{
          id: "doctor-1",
          fullName: "Bác sĩ An",
          specialization: "Nha khoa tổng quát",
          licenseNumber: "GP-001",
          bio: null,
          rating: 4.8,
          experienceYears: 8,
          workingStatus: "ACTIVE",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
        }],
        suggestedSlots: [{
          doctorId: "doctor-1",
          serviceId: "service-1",
          date: "2026-11-02",
          session: "MORNING",
          queueNumber: 1,
          sessionStartTime: "08:00:00",
          sessionEndTime: "11:00:00",
          estimatedStartTime: "08:00:00",
          estimatedStartAt: "2026-11-02T08:00:00+07:00",
        }],
      },
    };

    render(<MemoryRouter><ChatMessage message={message} /></MemoryRouter>);

    expect(screen.getByText("Khám răng tổng quát")).toBeInTheDocument();
    expect(screen.getByText("Bác sĩ An")).toBeInTheDocument();
    expect(screen.getByText(/Buổi sáng 08:00–11:00/)).toBeInTheDocument();
    expect(screen.getByText(/Dự kiến 08:00/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Sâu răng/ })).toHaveAttribute(
      "href",
      "https://www.nidcr.nih.gov/health-info/tooth-decay",
    );
    expect(screen.getByRole("link", { name: /Dự kiến 08:00/ })).toHaveAttribute(
      "href",
      expect.stringContaining("session=MORNING&queueNumber=1"),
    );
  });

  it("hiển thị câu trả lời Markdown cũ dưới dạng văn bản sạch", () => {
    const message: ChatMessageModel = {
      id: "message-markdown",
      sender: "ASSISTANT",
      content: "### Nguyên nhân\n*   **Sâu răng:** Có thể gây đau nhức.\n*   **Bệnh nha chu:** Có thể gây đau vùng hàm.",
      createdAt: "2026-11-02T01:00:00Z",
      metadata: {
        intents: ["SYMPTOM_CONSULTATION"],
        needsClarification: false,
        sources: [],
        suggestedServices: [],
        suggestedDoctors: [],
        suggestedSlots: [],
      },
    };

    const { container } = render(<MemoryRouter><ChatMessage message={message} /></MemoryRouter>);

    expect(container).toHaveTextContent("Nguyên nhân");
    expect(container).toHaveTextContent("- Sâu răng: Có thể gây đau nhức.");
    expect(container).toHaveTextContent("- Bệnh nha chu: Có thể gây đau vùng hàm.");
    expect(container.textContent).not.toContain("*");
  });
});
