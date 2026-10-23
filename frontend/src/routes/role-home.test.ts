import { describe, expect, it } from "vitest";
import { roleHome } from "./role-home";

describe("roleHome", () => {
  it("định tuyến đúng bốn vai trò", () => {
    expect(roleHome).toEqual({
      ADMIN: "/admin/dashboard",
      DOCTOR: "/doctor/dashboard",
      STAFF: "/staff/dashboard",
      PATIENT: "/patient/dashboard",
    });
  });
});
