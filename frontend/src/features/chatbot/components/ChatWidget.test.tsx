import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ChatWidget } from "./ChatWidget";

const authMock = vi.hoisted(() => ({
  state: "authenticated",
  user: { id: "patient-1", email: "patient@example.com", fullName: "Bệnh nhân", role: "PATIENT" },
}));

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: () => authMock,
}));

vi.mock("./ChatWindow", () => ({
  ChatWindow: ({ open }: { open: boolean }) => open ? <div role="dialog">Cửa sổ chatbot</div> : null,
}));

describe("ChatWidget", () => {
  afterEach(cleanup);

  beforeEach(() => {
    authMock.state = "authenticated";
    authMock.user = { id: "patient-1", email: "patient@example.com", fullName: "Bệnh nhân", role: "PATIENT" };
  });

  it("chỉ mở chatbot cho bệnh nhân đã đăng nhập", async () => {
    const user = userEvent.setup();
    render(<ChatWidget />);

    await user.click(screen.getByRole("button", { name: "Mở trợ lý nha khoa" }));

    expect(screen.getByRole("dialog")).toHaveTextContent("Cửa sổ chatbot");
  });

  it("không hiển thị chatbot cho vai trò khác", () => {
    authMock.user = { ...authMock.user, role: "DOCTOR" };
    render(<ChatWidget />);

    expect(screen.queryByRole("button", { name: "Mở trợ lý nha khoa" })).not.toBeInTheDocument();
  });
});
