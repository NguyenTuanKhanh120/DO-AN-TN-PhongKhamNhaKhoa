import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StatusBadge } from "./StatusBadge";

describe("StatusBadge", () => {
  it("uses the neutral user/service wording for shared ACTIVE status", () => {
    render(<StatusBadge status="ACTIVE" />);

    expect(screen.getByText("Đang hoạt động")).toBeInTheDocument();
  });

  it("keeps domain-specific labels for unambiguous statuses", () => {
    const { rerender } = render(<StatusBadge status="ON_LEAVE" />);
    expect(screen.getByText("Tạm nghỉ")).toBeInTheDocument();

    rerender(<StatusBadge status="PAID" />);
    expect(screen.getByText("Đã thanh toán")).toBeInTheDocument();
  });
});
