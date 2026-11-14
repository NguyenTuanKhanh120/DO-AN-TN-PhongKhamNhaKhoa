import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { DoctorSchedule, ScheduleSession } from "@/types/domain";
import { AdminDoctorWeeklySchedule } from "./AdminDoctorWeeklySchedule";

afterEach(cleanup);

const schedule = (
  id: string,
  dayOfWeek: number,
  session: ScheduleSession,
): DoctorSchedule => ({
  id,
  doctorId: "doctor-1",
  dayOfWeek,
  session,
  startTime: session === "MORNING" ? "08:00:00" : "14:00:00",
  endTime: session === "MORNING" ? "11:00:00" : "17:00:00",
  createdAt: "2026-11-12T00:00:00Z",
  updatedAt: "2026-11-12T00:00:00Z",
});

describe("AdminDoctorWeeklySchedule", () => {
  it("groups morning and afternoon schedules under one weekday", () => {
    render(
      <AdminDoctorWeeklySchedule
        schedules={[
          schedule("monday-morning", 1, "MORNING"),
          schedule("monday-afternoon", 1, "AFTERNOON"),
        ]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    expect(screen.getAllByText("Thứ Hai")).toHaveLength(1);
    expect(screen.getByText("Buổi sáng")).toBeInTheDocument();
    expect(screen.getByText("Buổi chiều")).toBeInTheDocument();
    expect(screen.getByText("08:00–11:00")).toBeInTheDocument();
    expect(screen.getByText("14:00–17:00")).toBeInTheDocument();
  });

  it("keeps edit and delete actions bound to each session", async () => {
    const user = userEvent.setup();
    const morning = schedule("monday-morning", 1, "MORNING");
    const afternoon = schedule("monday-afternoon", 1, "AFTERNOON");
    const onEdit = vi.fn();
    const onDelete = vi.fn();

    render(
      <AdminDoctorWeeklySchedule
        schedules={[morning, afternoon]}
        onEdit={onEdit}
        onDelete={onDelete}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Sửa Buổi sáng Thứ Hai" }));
    await user.click(screen.getByRole("button", { name: "Xóa Buổi chiều Thứ Hai" }));

    expect(onEdit).toHaveBeenCalledWith(morning);
    expect(onDelete).toHaveBeenCalledWith(afternoon);
  });
});
