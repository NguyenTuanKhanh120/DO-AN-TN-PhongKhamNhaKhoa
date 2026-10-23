import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import LoginPage from "./LoginPage";

const authMocks = vi.hoisted(() => ({ signIn: vi.fn() }));

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: () => ({ signIn: authMocks.signIn }),
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/patient/dashboard" element={<p>Patient dashboard</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("LoginPage", () => {
  beforeEach(() => authMocks.signIn.mockReset());

  it("đăng nhập thành công và điều hướng theo role", async () => {
    authMocks.signIn.mockResolvedValue({ id: "1", email: "patient@example.com", fullName: "Nguyễn Văn A", role: "PATIENT" });
    renderPage();
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/Email/), "patient@example.com");
    await user.type(screen.getByLabelText(/Mật khẩu/), "Patient@123");
    await user.click(screen.getByRole("button", { name: /Đăng nhập/ }));
    expect(await screen.findByText("Patient dashboard")).toBeInTheDocument();
  });

  it("giữ email khi validation mật khẩu thất bại", async () => {
    renderPage();
    const user = userEvent.setup();
    const email = screen.getByLabelText(/Email/);
    await user.type(email, "patient@example.com");
    await user.click(screen.getByRole("button", { name: /Đăng nhập/ }));
    expect(await screen.findByText("Mật khẩu không được để trống")).toBeInTheDocument();
    expect(email).toHaveValue("patient@example.com");
    expect(authMocks.signIn).not.toHaveBeenCalled();
  });
});
