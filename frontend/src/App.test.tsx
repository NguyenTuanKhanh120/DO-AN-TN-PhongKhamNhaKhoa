import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { AuthProvider } from "@/contexts/AuthContext";
import { ProtectedRoute, PublicOnlyRoute } from "@/routes/guards";
import { ApiError } from "@/services/api/errors";
import { emitSessionEvent } from "@/services/api/session-events";
import { tokenStore } from "@/services/api/token-store";
import type { AuthResponse, AuthSession, Role } from "@/types/domain";

const mocks = vi.hoisted(() => ({
  refreshAccessToken: vi.fn(),
  getSession: vi.fn(),
  recordActivity: vi.fn(),
  unlock: vi.fn(),
  logout: vi.fn(),
}));

vi.mock("@/services/api/client", () => ({ refreshAccessToken: mocks.refreshAccessToken }));
vi.mock("@/features/auth/services/auth-service", () => mocks);
vi.mock("@/routes/AppRoutes", () => ({
  AppRoutes: () => (
    <Routes>
      <Route path="/login" element={<PublicOnlyRoute><h1>Đăng nhập</h1></PublicOnlyRoute>} />
      <Route path="/admin/users" element={<ProtectedRoute roles={["ADMIN"]}><h1>Dữ liệu quản trị</h1></ProtectedRoute>} />
      <Route path="/forbidden" element={<h1>Không có quyền</h1>} />
      <Route path="*" element={<ProtectedRoute roles={["ADMIN", "DOCTOR", "STAFF", "PATIENT"]}><main><h1>Ca khám</h1><label>Bệnh án<textarea /></label></main></ProtectedRoute>} />
    </Routes>
  ),
}));
vi.mock("@/features/chatbot", () => ({ ChatWidget: () => <p>Chatbot</p> }));

const START = new Date("2026-11-26T01:00:00Z");
const MINUTE = 60_000;

function auth(role: Role = "ADMIN"): AuthResponse {
  return {
    accessToken: "test-access-token",
    tokenType: "Bearer",
    expiresInSeconds: 900,
    userId: "user-1",
    email: "user@example.com",
    fullName: "Nguyễn Văn A",
    role,
  };
}

function session(role: Role = "ADMIN", overrides: Partial<AuthSession> = {}): AuthSession {
  return {
    sessionId: "session-1",
    role,
    status: "ACTIVE",
    loggedInAt: START.toISOString(),
    absoluteExpiresAt: new Date(START.getTime() + 12 * 60 * MINUTE).toISOString(),
    lastActivityAt: START.toISOString(),
    lockedAt: null,
    idleTimeoutSeconds: role === "ADMIN" ? 900 : role === "PATIENT" ? null : 1200,
    idleLockEnabled: role !== "PATIENT",
    ...overrides,
  };
}

function LocationProbe() {
  return <span data-testid="location">{useLocation().pathname}</span>;
}

async function renderApp(role: Role = "ADMIN", path = "/work") {
  mocks.refreshAccessToken.mockResolvedValue(auth(role));
  mocks.getSession.mockResolvedValueOnce(session(role));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
  let view!: ReturnType<typeof render>;
  await act(async () => {
    view = render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={[path]}><AuthProvider><App /><LocationProbe /></AuthProvider></MemoryRouter></QueryClientProvider>);
  });
  return view;
}

async function advance(ms: number) {
  await act(async () => { await vi.advanceTimersByTimeAsync(ms); });
}

function lockDialog() {
  return screen.queryByRole("dialog", { name: "Phiên làm việc đã khóa" });
}

describe("idle session screen protection", () => {
  beforeAll(() => {
    // jsdom does not implement the native dialog API; browser coverage exercises its focus isolation.
    Object.defineProperty(HTMLDialogElement.prototype, "showModal", { configurable: true, value(this: HTMLDialogElement) { this.open = true; } });
    Object.defineProperty(HTMLDialogElement.prototype, "close", { configurable: true, value(this: HTMLDialogElement) { this.open = false; } });
  });

  afterAll(() => {
    Reflect.deleteProperty(HTMLDialogElement.prototype, "showModal");
    Reflect.deleteProperty(HTMLDialogElement.prototype, "close");
  });

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(START);
    vi.resetAllMocks();
    mocks.getSession.mockResolvedValue(session());
    mocks.recordActivity.mockImplementation(async () => session("ADMIN", { lastActivityAt: new Date().toISOString() }));
  });

  afterEach(() => {
    cleanup();
    tokenStore.clear();
    vi.useRealTimers();
  });

  it.each(["ADMIN", "DOCTOR", "STAFF"] as const)("restores a locked %s session after reload without rendering protected data", async (role) => {
    tokenStore.set("expired-token");
    mocks.refreshAccessToken.mockRejectedValueOnce(new ApiError("Phiên đã khóa", "SESSION_LOCKED", 423));
    await renderApp(role);
    expect(lockDialog()).toBeInTheDocument();
    expect(screen.getByTestId("location")).toHaveTextContent("/work");
    expect(screen.queryByText("Ca khám")).not.toBeInTheDocument();
    expect(screen.getByText("Nhập lại mật khẩu để mở khóa phiên làm việc.")).toBeInTheDocument();
    expect(mocks.getSession).not.toHaveBeenCalled();
    expect(tokenStore.get()).toBeNull();

    mocks.unlock.mockResolvedValueOnce(auth(role));
    const password = screen.getByLabelText(/Mật khẩu/, { selector: "input" });
    fireEvent.change(password, { target: { value: "Password123" } });
    await act(async () => { fireEvent.submit(password.closest("form")!); });
    expect(lockDialog()).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Bệnh án" })).toBeInTheDocument();
    expect(screen.getByTestId("location")).toHaveTextContent("/work");
    expect(tokenStore.get()).toBe("test-access-token");
  });

  it("shows the unlock screen instead of the login form on a locked public-only route", async () => {
    mocks.refreshAccessToken.mockRejectedValueOnce(new ApiError("Phiên đã khóa", "SESSION_LOCKED", 423));
    await renderApp("ADMIN", "/login");
    expect(lockDialog()).toBeInTheDocument();
    expect(screen.queryByText("Đăng nhập")).not.toBeInTheDocument();
    mocks.unlock.mockResolvedValueOnce(auth());
    const password = screen.getByLabelText(/Mật khẩu/, { selector: "input" });
    fireEvent.change(password, { target: { value: "Password123" } });
    await act(async () => { fireEvent.submit(password.closest("form")!); });
    expect(screen.getByTestId("location")).toHaveTextContent("/admin/dashboard");
  });

  it("keeps a restored session locked when the unlock password is wrong", async () => {
    mocks.refreshAccessToken.mockRejectedValueOnce(new ApiError("Phiên đã khóa", "SESSION_LOCKED", 423));
    mocks.unlock.mockRejectedValueOnce(new ApiError("Mật khẩu không chính xác", "INVALID_CREDENTIALS", 401));
    await renderApp();
    const password = screen.getByLabelText(/Mật khẩu/, { selector: "input" });
    fireEvent.change(password, { target: { value: "WrongPassword" } });
    await act(async () => { fireEvent.submit(password.closest("form")!); });
    expect(lockDialog()).toBeInTheDocument();
    expect(screen.getByText("Mật khẩu không chính xác")).toBeInTheDocument();
    expect(screen.getByTestId("location")).toHaveTextContent("/work");
    expect(tokenStore.get()).toBeNull();
  });

  it.each(["SESSION_EXPIRED", "SESSION_REVOKED", "REFRESH_TOKEN_INVALID", "REFRESH_TOKEN_REUSED"])(
    "redirects to login when reload returns %s instead of SESSION_LOCKED", async (code) => {
      mocks.refreshAccessToken.mockRejectedValueOnce(new ApiError("Đăng nhập lại", code, 401));
      await renderApp();
      expect(lockDialog()).not.toBeInTheDocument();
      expect(screen.getByRole("heading", { name: "Đăng nhập" })).toBeInTheDocument();
      expect(screen.getByTestId("location")).toHaveTextContent("/login");
    },
  );

  it.each([
    ["SESSION_EXPIRED", 401], ["SESSION_REVOKED", 401], ["REFRESH_TOKEN_INVALID", 401], ["ACCOUNT_INACTIVE", 403],
  ] as const)("returns to login if the locked session becomes unusable: %s", async (code, status) => {
    mocks.refreshAccessToken.mockRejectedValueOnce(new ApiError("Phiên đã khóa", "SESSION_LOCKED", 423));
    mocks.unlock.mockRejectedValueOnce(new ApiError("Đăng nhập lại", code, status));
    await renderApp();
    const password = screen.getByLabelText(/Mật khẩu/, { selector: "input" });
    fireEvent.change(password, { target: { value: "Password123" } });
    await act(async () => { fireEvent.submit(password.closest("form")!); });
    expect(lockDialog()).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Đăng nhập" })).toBeInTheDocument();
    expect(tokenStore.get()).toBeNull();
  });

  it("can log out of a restored locked session without unlocking first", async () => {
    mocks.refreshAccessToken.mockRejectedValueOnce(new ApiError("Phiên đã khóa", "SESSION_LOCKED", 423));
    mocks.logout.mockResolvedValueOnce(undefined);
    await renderApp();
    await act(async () => { fireEvent.click(screen.getByRole("button", { name: "Đăng xuất tài khoản" })); });
    expect(mocks.logout).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("heading", { name: "Đăng nhập" })).toBeInTheDocument();
  });

  it.each([
    new ApiError("Không thể kết nối", "NETWORK_ERROR"),
    new ApiError("Yêu cầu CSRF không hợp lệ", "CSRF_VALIDATION_FAILED", 403),
  ])("keeps the screen locked for a recoverable unlock failure: $code", async (error) => {
    mocks.refreshAccessToken.mockRejectedValueOnce(new ApiError("Phiên đã khóa", "SESSION_LOCKED", 423));
    mocks.unlock.mockRejectedValueOnce(error);
    await renderApp();
    const password = screen.getByLabelText(/Mật khẩu/, { selector: "input" });
    fireEvent.change(password, { target: { value: "Password123" } });
    await act(async () => { fireEvent.submit(password.closest("form")!); });
    expect(lockDialog()).toBeInTheDocument();
    expect(screen.getByText(error.message)).toBeInTheDocument();
    expect(tokenStore.get()).toBeNull();
  });

  it("still checks the actual role after unlocking a protected URL", async () => {
    mocks.refreshAccessToken.mockRejectedValueOnce(new ApiError("Phiên đã khóa", "SESSION_LOCKED", 423));
    mocks.unlock.mockResolvedValueOnce(auth("DOCTOR"));
    await renderApp("DOCTOR", "/admin/users");
    const password = screen.getByLabelText(/Mật khẩu/, { selector: "input" });
    fireEvent.change(password, { target: { value: "Password123" } });
    await act(async () => { fireEvent.submit(password.closest("form")!); });
    expect(screen.getByRole("heading", { name: "Không có quyền" })).toBeInTheDocument();
    expect(screen.queryByText("Dữ liệu quản trị")).not.toBeInTheDocument();
  });

  it.each([ ["ADMIN", 15], ["DOCTOR", 20], ["STAFF", 20] ] as const)(
    "%s locks after %i minutes without another API request",
    async (role, minutes) => {
      await renderApp(role);
      await advance(minutes * MINUTE - 1);
      expect(lockDialog()).not.toBeInTheDocument();
      await advance(1);
      expect(lockDialog()).toBeInTheDocument();
      expect(mocks.getSession).toHaveBeenCalledTimes(1);
      expect(mocks.recordActivity).not.toHaveBeenCalled();
    },
  );

  it("uses the backend policy and existing lastActivityAt instead of restarting the timeout on mount", async () => {
    mocks.getSession.mockResolvedValueOnce(session("ADMIN", {
      idleTimeoutSeconds: 42,
      lastActivityAt: new Date(START.getTime() - 10_000).toISOString(),
    }));
    await renderApp();
    await advance(31_999);
    expect(lockDialog()).not.toBeInTheDocument();
    await advance(1);
    expect(lockDialog()).toBeInTheDocument();
  });

  it("does not apply idle locking or send activity for PATIENT", async () => {
    await renderApp("PATIENT");
    fireEvent.keyDown(window, { key: "a" });
    fireEvent.pointerDown(window);
    await advance(24 * 60 * MINUTE);
    expect(lockDialog()).not.toBeInTheDocument();
    expect(mocks.getSession).not.toHaveBeenCalled();
    expect(mocks.recordActivity).not.toHaveBeenCalled();
  });

  it("honors an explicitly disabled backend idle lock", async () => {
    mocks.getSession.mockResolvedValueOnce(session("ADMIN", { idleLockEnabled: false, idleTimeoutSeconds: null }));
    await renderApp();
    await advance(30 * MINUTE);
    expect(lockDialog()).not.toBeInTheDocument();
  });

  it("extends the deadline only with acknowledged real activity and throttles repeated interactions", async () => {
    await renderApp();
    await advance(10 * MINUTE);
    await act(async () => { fireEvent.keyDown(window, { key: "a" }); });
    await advance(MINUTE - 1);
    fireEvent.pointerDown(window);
    expect(mocks.recordActivity).toHaveBeenCalledTimes(1);
    await advance(14 * MINUTE);
    expect(lockDialog()).not.toBeInTheDocument();
    await advance(1);
    expect(lockDialog()).toBeInTheDocument();
  });

  it("does not let a failed activity request extend the confirmed deadline", async () => {
    await renderApp();
    mocks.recordActivity.mockRejectedValueOnce(new Error("network unavailable"));
    await advance(14 * MINUTE);
    await act(async () => { fireEvent.pointerDown(window); });
    await advance(MINUTE);
    expect(lockDialog()).toBeInTheDocument();
  });

  it("retries loading the policy after a network failure without resetting lastActivityAt", async () => {
    mocks.getSession.mockRejectedValueOnce(new Error("network unavailable"));
    await renderApp();
    await advance(5_000);
    expect(mocks.getSession).toHaveBeenCalledTimes(2);
    await advance(15 * MINUTE - 5_000);
    expect(lockDialog()).toBeInTheDocument();
  });

  it("ignores older session timestamps arriving after an activity acknowledgement", async () => {
    let resolveSession!: (value: AuthSession) => void;
    mocks.getSession.mockReturnValueOnce(new Promise<AuthSession>((resolve) => { resolveSession = resolve; }));
    await renderApp();
    await advance(5 * MINUTE);
    await act(async () => { fireEvent.keyDown(window, { key: "a" }); });
    await act(async () => { resolveSession(session()); });
    await advance(15 * MINUTE - 1);
    expect(lockDialog()).not.toBeInTheDocument();
    await advance(1);
    expect(lockDialog()).toBeInTheDocument();
  });

  it.each(["focus", "pageshow", "visibilitychange"])("checks expiry on %s after a suspended tab resumes", async (event) => {
    await renderApp();
    vi.setSystemTime(new Date(START.getTime() + 16 * MINUTE));
    if (event === "visibilitychange") fireEvent(document, new Event(event));
    else fireEvent(window, new Event(event));
    expect(lockDialog()).toBeInTheDocument();
    expect(mocks.recordActivity).not.toHaveBeenCalled();
  });

  it("blocks the first expired interaction before it can reach a form or renew activity", async () => {
    await renderApp();
    const field = screen.getByRole("textbox", { name: "Bệnh án" });
    const onKeyDown = vi.fn();
    field.addEventListener("keydown", onKeyDown);
    vi.setSystemTime(new Date(START.getTime() + 15 * MINUTE));
    expect(fireEvent.keyDown(field, { key: "Enter" })).toBe(false);
    expect(onKeyDown).not.toHaveBeenCalled();
    expect(mocks.recordActivity).not.toHaveBeenCalled();
    expect(lockDialog()).toBeInTheDocument();
  });

  it("hides and disables the background while preserving unsaved form values through password unlock", async () => {
    await renderApp();
    const field = screen.getByRole("textbox", { name: "Bệnh án" });
    fireEvent.change(field, { target: { value: "Nội dung đang nhập" } });
    await advance(15 * MINUTE);

    const background = field.closest("[inert]");
    expect(background).toHaveAttribute("aria-hidden", "true");
    expect(background).toHaveClass("invisible");
    expect(screen.queryByRole("textbox", { name: "Bệnh án" })).not.toBeInTheDocument();
    expect(lockDialog()).toHaveClass("bg-slate-950");

    mocks.unlock.mockResolvedValueOnce(auth());
    mocks.getSession.mockResolvedValueOnce(session("ADMIN", { lastActivityAt: new Date().toISOString() }));
    const password = screen.getByLabelText(/Mật khẩu/, { selector: "input" });
    fireEvent.change(password, { target: { value: "Password123" } });
    await act(async () => { fireEvent.submit(password.closest("form")!); });

    expect(mocks.unlock).toHaveBeenCalledWith("Password123");
    expect(lockDialog()).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Bệnh án" })).toBe(field);
    expect(field).toHaveValue("Nội dung đang nhập");
    expect(field.closest("[inert]")).toBeNull();
    await advance(15 * MINUTE);
    expect(lockDialog()).toBeInTheDocument();
  });

  it("still protects the screen when the API emits SESSION_LOCKED", async () => {
    await renderApp();
    act(() => { emitSessionEvent("locked"); });
    expect(lockDialog()).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Bệnh án" })).not.toBeInTheDocument();
  });

  it("cannot be dismissed with Escape and keeps keyboard events out of background dialogs", async () => {
    await renderApp();
    await advance(15 * MINUTE);
    const onKeyDown = vi.fn();
    document.addEventListener("keydown", onKeyDown);
    const password = screen.getByLabelText(/Mật khẩu/, { selector: "input" });
    fireEvent.keyDown(password, { key: "Escape" });
    expect(onKeyDown).not.toHaveBeenCalled();
    document.removeEventListener("keydown", onKeyDown);
    const dialog = screen.getByRole("dialog", { name: "Phiên làm việc đã khóa" });
    expect(fireEvent(dialog, new Event("cancel", { cancelable: true }))).toBe(false);
    expect(dialog).toHaveAttribute("open");
  });

  it("does not unlock the UI when an old activity response arrives after the lock", async () => {
    let resolveActivity!: (value: AuthSession) => void;
    mocks.recordActivity.mockReturnValueOnce(new Promise<AuthSession>((resolve) => { resolveActivity = resolve; }));
    await renderApp();
    await advance(14 * MINUTE);
    fireEvent.pointerDown(window);
    await advance(MINUTE);
    await act(async () => { resolveActivity(session("ADMIN", { lastActivityAt: new Date().toISOString() })); });
    expect(lockDialog()).toBeInTheDocument();
  });

  it("cleans timers and listeners on unmount and ignores late policy responses", async () => {
    let resolveSession!: (value: AuthSession) => void;
    mocks.getSession.mockReturnValueOnce(new Promise<AuthSession>((resolve) => { resolveSession = resolve; }));
    const view = await renderApp();
    view.unmount();
    await act(async () => { resolveSession(session()); });
    fireEvent.pointerDown(window);
    expect(mocks.recordActivity).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });
});
