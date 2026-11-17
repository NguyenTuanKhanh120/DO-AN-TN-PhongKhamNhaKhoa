import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import RegisterPage from "./RegisterPage";

const authMocks = vi.hoisted(() => ({ signUp: vi.fn() }));
const apiErrorMocks = vi.hoisted(() => ({ toApiError: vi.fn() }));

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: () => ({ signUp: authMocks.signUp }),
}));

vi.mock("@/services/api/errors", () => ({
  toApiError: apiErrorMocks.toApiError,
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/register"]}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/patient/dashboard" element={<p>Patient dashboard</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/Họ và tên/), "Nguyễn Văn A");
  await user.type(screen.getByLabelText(/Số điện thoại/), "0912345678");
  await user.type(screen.getByLabelText(/Email/), "patient@example.com");
  await user.type(screen.getByLabelText(/^Mật khẩu/), "Patient@123");
  await user.type(screen.getByLabelText(/Xác nhận mật khẩu/), "Patient@123");
}

describe("RegisterPage", () => {
  beforeEach(() => {
    authMocks.signUp.mockReset();
    apiErrorMocks.toApiError.mockReset();
  });
  afterEach(cleanup);

  it("hiển thị phone, lọc ký tự không phải số, giới hạn 10 số và gửi phone khi đăng ký", async () => {
    authMocks.signUp.mockResolvedValue({
      id: "patient-1",
      email: "patient@example.com",
      fullName: "Nguyễn Văn A",
      role: "PATIENT",
    });
    renderPage();
    const user = userEvent.setup();
    const phone = screen.getByLabelText(/Số điện thoại/);

    expect(phone).toHaveAttribute("type", "tel");
    expect(phone).toHaveAttribute("inputmode", "numeric");
    expect(phone).toHaveAttribute("maxlength", "10");

    await user.type(screen.getByLabelText(/Họ và tên/), "Nguyễn Văn A");
    await user.type(phone, "09abc12 +34-56789");
    await user.type(screen.getByLabelText(/Email/), "patient@example.com");
    await user.type(screen.getByLabelText(/^Mật khẩu/), "Patient@123");
    await user.type(screen.getByLabelText(/Xác nhận mật khẩu/), "Patient@123");
    expect(phone).toHaveValue("0912345678");

    await user.click(screen.getByRole("button", { name: /Đăng ký/ }));

    expect(authMocks.signUp).toHaveBeenCalledWith(
      "Nguyễn Văn A",
      "0912345678",
      "patient@example.com",
      "Patient@123",
    );
    expect(await screen.findByText("Patient dashboard")).toBeInTheDocument();
  });

  it("hiển thị lỗi bắt buộc ngay dưới trường số điện thoại", async () => {
    renderPage();
    await userEvent.setup().click(screen.getByRole("button", { name: /Đăng ký/ }));
    expect(await screen.findByText("Số điện thoại không được để trống")).toBeInTheDocument();
    expect(authMocks.signUp).not.toHaveBeenCalled();
  });

  it("gắn lỗi field phone từ Backend vào đúng input", async () => {
    authMocks.signUp.mockRejectedValue(new Error("Bad request"));
    apiErrorMocks.toApiError.mockReturnValue({
      message: "Dữ liệu đầu vào không hợp lệ",
      fields: [{ field: "phone", message: "Số điện thoại không hợp lệ từ máy chủ" }],
    });
    renderPage();
    const user = userEvent.setup();
    await fillValidForm(user);

    const form = screen.getByRole("button", { name: /Đăng ký/ }).closest("form");
    expect(form).not.toBeNull();
    fireEvent.submit(form!);

    expect(await screen.findByText("Số điện thoại không hợp lệ từ máy chủ")).toBeInTheDocument();
  });
});
