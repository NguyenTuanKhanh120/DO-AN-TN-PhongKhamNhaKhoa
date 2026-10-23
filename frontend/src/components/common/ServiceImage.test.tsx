import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ServiceImage } from "./ServiceImage";

describe("ServiceImage", () => {
  it("hiển thị placeholder khi dịch vụ chưa có ảnh", () => {
    render(<ServiceImage src={null} alt="Tẩy trắng răng" />);

    expect(screen.getByRole("img", { name: "Chưa có ảnh cho Tẩy trắng răng" })).toBeInTheDocument();
  });

  it("chuyển sang placeholder khi URL ảnh không tải được", () => {
    render(<ServiceImage src="https://example.com/service.jpg" alt="Niềng răng" />);
    fireEvent.error(screen.getByRole("img", { name: "Niềng răng" }));

    expect(screen.getByRole("img", { name: "Chưa có ảnh cho Niềng răng" })).toBeInTheDocument();
  });
});
