import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { RevenueSummary } from "@/types/domain";
import { useRevenue } from "../hooks/useAdminData";
import { AdminRevenueSummary } from "./AdminRevenueSummary";

vi.mock("../hooks/useAdminData", () => ({
  useRevenue: vi.fn(),
}));

const revenue = (rangeRevenue: number, monthlyRevenue: number): RevenueSummary => ({
  todayRevenue: 0,
  rangeFrom: "2026-01-01",
  rangeTo: "2026-12-31",
  rangeRevenue,
  month: "2026-11",
  monthlyRevenue,
  totalPaidPayments: 0,
  totalUnpaidPayments: 0,
  totalUnpaidAmount: 0,
});

const mockedUseRevenue = vi.mocked(useRevenue, { partial: true });

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-12T04:00:00Z"));
  mockedUseRevenue.mockReset();
  mockedUseRevenue.mockImplementation((params) => ({
    data: params.month ? revenue(0, 2_000_000) : revenue(3_000_000, 0),
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }));
});

describe("AdminRevenueSummary", () => {
  it("shows only the currently selected revenue category", () => {
    render(<AdminRevenueSummary />);

    expect(screen.getByLabelText("Chọn ngày")).toBeInTheDocument();
    expect(screen.queryByLabelText("Chọn tháng")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Chọn năm")).not.toBeInTheDocument();
    expect(screen.getByText("3.000.000 ₫")).toBeInTheDocument();
    expect(mockedUseRevenue).toHaveBeenCalledTimes(1);
  });

  it("switches the selector and query between month and year", () => {
    render(<AdminRevenueSummary />);

    fireEvent.click(screen.getByRole("button", { name: "Theo tháng" }));
    expect(screen.queryByLabelText("Chọn ngày")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Chọn tháng")).toBeInTheDocument();
    expect(screen.getByText("2.000.000 ₫")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Chọn tháng"), {
      target: { value: "2026-09" },
    });
    expect(mockedUseRevenue).toHaveBeenCalledWith({ month: "2026-09" }, true);

    fireEvent.click(screen.getByRole("button", { name: "Theo năm" }));
    expect(screen.queryByLabelText("Chọn tháng")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Chọn năm")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Chọn năm"), {
      target: { value: "2025" },
    });
    expect(mockedUseRevenue).toHaveBeenCalledWith(
      { fromDate: "2025-01-01", toDate: "2025-12-31" },
      true,
    );
  });

  it("rejects future day, month and year selections", () => {
    render(<AdminRevenueSummary />);

    const dayInput = screen.getByLabelText("Chọn ngày");
    expect(dayInput).toHaveAttribute("max", "2026-11-12");
    fireEvent.change(dayInput, { target: { value: "2100-01-01" } });
    expect(screen.getByText("Không thể chọn ngày trong tương lai")).toBeInTheDocument();
    expect(mockedUseRevenue).toHaveBeenLastCalledWith({}, false);

    fireEvent.click(screen.getByRole("button", { name: "Theo tháng" }));
    const monthInput = screen.getByLabelText("Chọn tháng");
    expect(monthInput).toHaveAttribute("max", "2026-11");
    fireEvent.change(monthInput, { target: { value: "2100-01" } });
    expect(screen.getByText("Không thể chọn tháng trong tương lai")).toBeInTheDocument();
    expect(mockedUseRevenue).toHaveBeenLastCalledWith({}, false);

    fireEvent.click(screen.getByRole("button", { name: "Theo năm" }));
    const yearInput = screen.getByLabelText("Chọn năm");
    expect(yearInput).toHaveAttribute("max", "2026");
    fireEvent.change(yearInput, { target: { value: "2100" } });
    expect(screen.getByText("Không thể chọn năm trong tương lai")).toBeInTheDocument();
    expect(mockedUseRevenue).toHaveBeenLastCalledWith({}, false);
  });
});
