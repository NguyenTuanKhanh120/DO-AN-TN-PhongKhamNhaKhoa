import { expect, test } from "@playwright/test";

test("idle lock isolates open dialogs and preserves their form through password unlock", async ({ page }, testInfo) => {
  const start = new Date("2026-11-26T01:00:00Z");
  await page.clock.install({ time: start });
  const auth = {
    accessToken: "test-admin-token", tokenType: "Bearer", expiresInSeconds: 900,
    userId: "admin-1", email: "admin@example.com", fullName: "Quản trị viên", role: "ADMIN",
  };
  let lastActivityAt = start.toISOString();
  let activityRequests = 0;
  let unlockPassword: string | undefined;
  const session = () => ({
    sessionId: "session-1", role: "ADMIN", status: "ACTIVE", loggedInAt: start.toISOString(),
    absoluteExpiresAt: new Date(start.getTime() + 8 * 60 * 60_000).toISOString(),
    lastActivityAt, lockedAt: null, idleTimeoutSeconds: 900, idleLockEnabled: true,
  });
  await page.route((url) => url.pathname.startsWith("/api/"), async (route) => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown;
    switch (path) {
      case "/api/auth/refresh": data = auth; break;
      case "/api/auth/session": data = session(); break;
      case "/api/auth/activity":
        activityRequests += 1;
        lastActivityAt = await page.evaluate(() => new Date().toISOString());
        data = session();
        break;
      case "/api/auth/unlock":
        unlockPassword = route.request().postDataJSON().password;
        lastActivityAt = await page.evaluate(() => new Date().toISOString());
        data = auth;
        break;
      case "/api/admin/users":
        data = {
          content: [{ id: "patient-1", fullName: "Bệnh nhân thử nghiệm", email: "patient@example.com", role: "PATIENT", status: "ACTIVE" }],
          page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true,
        };
        break;
      default: throw new Error(`Unexpected API request: ${path}`);
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ success: true, code: "SUCCESS", message: "Thành công", data }) });
  });

  await page.goto("/admin/users");
  await page.getByRole("button", { name: "Sửa", exact: true }).click();
  const editDialog = page.getByRole("dialog", { name: "Sửa thông tin tài khoản" });
  await editDialog.getByLabel(/Họ và tên/).fill("Nội dung chưa lưu");
  await expect.poll(() => activityRequests).toBe(1);
  await page.clock.fastForward(15 * 60_000 + 1_000);

  const lock = page.getByRole("dialog", { name: "Phiên làm việc đã khóa" });
  await expect(lock).toBeVisible();
  expect(await lock.evaluate((element) => element.matches(":modal"))).toBe(true);
  expect(activityRequests).toBe(1);
  await page.keyboard.press("Escape");
  await expect(lock).toBeVisible();

  // The other dialog is a portal outside App's wrapper. Native modal isolation must still block it.
  await editDialog.getByLabel(/Họ và tên/).evaluate((element: HTMLInputElement) => element.focus());
  expect(await lock.evaluate((element) => element.contains(document.activeElement))).toBe(true);
  for (let index = 0; index < 6; index += 1) {
    await page.keyboard.press("Tab");
    // Chromium may move focus to browser chrome (reported as body), but never to a background control.
    expect(await lock.evaluate((element) => document.activeElement === document.body || element.contains(document.activeElement))).toBe(true);
  }
  const backgroundAlpha = await lock.evaluate((element) => {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");
    if (!context) throw new Error("Canvas is unavailable");
    context.fillStyle = getComputedStyle(element).backgroundColor;
    context.fillRect(0, 0, 1, 1);
    return context.getImageData(0, 0, 1, 1).data[3];
  });
  expect(backgroundAlpha).toBe(255);
  expect(await lock.boundingBox()).toMatchObject({ x: 0, y: 0, ...page.viewportSize() });
  await page.screenshot({ path: testInfo.outputPath("session-locked.png"), fullPage: true });

  await lock.getByLabel(/Mật khẩu/).fill("Password123");
  await lock.getByRole("button", { name: "Mở khóa phiên" }).click();
  await expect(lock).not.toBeVisible();
  expect(unlockPassword).toBe("Password123");
  await expect(editDialog).toBeVisible();
  await expect(editDialog.getByLabel(/Họ và tên/)).toHaveValue("Nội dung chưa lưu");
});

for (const flow of ["reload", "expired access token"] as const) {
  test(`restores a locked session after ${flow} and resumes the same page after unlock`, async ({ page, context }) => {
    const auth = {
      accessToken: "test-admin-token", tokenType: "Bearer", expiresInSeconds: 900,
      userId: "admin-1", email: "admin@example.com", fullName: "Quản trị viên", role: "ADMIN",
    };
    let locked = false;
    let expiredAccessToken = false;
    let refreshRequests = 0;
    let dataLoads = 0;
    let lastAuthorization: string | undefined;
    let unlockCsrf: string | undefined;
    await context.addCookies([{ name: "XSRF-TOKEN", value: "test-csrf", url: "http://127.0.0.1:5173" }]);
    await page.route((url) => url.pathname.startsWith("/api/"), async (route) => {
      const path = new URL(route.request().url()).pathname;
      const success = (data: unknown) => route.fulfill({
        status: 200, contentType: "application/json", body: JSON.stringify({ success: true, code: "SUCCESS", message: "Thành công", data }),
      });
      const failure = (status: number, code: string, message: string) => route.fulfill({
        status, contentType: "application/json", body: JSON.stringify({ success: false, code, message, data: null }),
      });
      switch (path) {
        case "/api/auth/refresh":
          refreshRequests += 1;
          return locked ? failure(423, "SESSION_LOCKED", "Phiên đã khóa") : success(auth);
        case "/api/auth/unlock":
          unlockCsrf = route.request().headers()["x-xsrf-token"];
          if (route.request().postDataJSON().password !== "Password123") {
            return failure(401, "INVALID_CREDENTIALS", "Mật khẩu không chính xác");
          }
          locked = false;
          expiredAccessToken = false;
          return success({ ...auth, accessToken: "unlocked-admin-token" });
        case "/api/auth/session":
        case "/api/auth/activity": {
          if (locked) return failure(423, "SESSION_LOCKED", "Phiên đã khóa");
          const now = new Date();
          return success({
            sessionId: "session-1", role: "ADMIN", status: "ACTIVE", loggedInAt: now.toISOString(),
            absoluteExpiresAt: new Date(now.getTime() + 8 * 60 * 60_000).toISOString(),
            lastActivityAt: now.toISOString(), lockedAt: null, idleTimeoutSeconds: 900, idleLockEnabled: true,
          });
        }
        case "/api/admin/users":
          if (expiredAccessToken) return failure(401, "AUTHENTICATION_REQUIRED", "Access Token hết hạn");
          if (locked) return failure(423, "SESSION_LOCKED", "Phiên đã khóa");
          dataLoads += 1;
          lastAuthorization = route.request().headers().authorization;
          return success({
            content: [{ id: "patient-1", fullName: "Bệnh nhân thử nghiệm", email: "patient@example.com", role: "PATIENT", status: "ACTIVE" }],
            page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true,
          });
        default: throw new Error(`Unexpected API request: ${path}`);
      }
    });

    await page.goto("/admin/users");
    await expect(page.getByText("Bệnh nhân thử nghiệm", { exact: true })).toBeVisible();
    expect(lastAuthorization).toBe("Bearer test-admin-token");
    const initialDataLoads = dataLoads;
    locked = true;
    if (flow === "reload") {
      await page.reload();
    } else {
      expiredAccessToken = true;
      await page.getByPlaceholder("Tìm email hoặc họ tên").fill("test");
    }
    const lock = page.getByRole("dialog", { name: "Phiên làm việc đã khóa" });
    await expect(lock).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/users$/);
    expect(refreshRequests).toBe(2);
    expect(dataLoads).toBe(initialDataLoads);
    if (flow === "reload") {
      await expect(page.getByText("Bệnh nhân thử nghiệm", { exact: true })).toHaveCount(0);
      await page.reload();
      await expect(lock).toBeVisible();
      expect(dataLoads).toBe(initialDataLoads);
    }

    await lock.getByLabel(/Mật khẩu/).fill("WrongPassword");
    await lock.getByRole("button", { name: "Mở khóa phiên" }).click();
    await expect(lock.getByText("Mật khẩu không chính xác")).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/users$/);
    await lock.getByLabel(/Mật khẩu/).fill("Password123");
    await lock.getByRole("button", { name: "Mở khóa phiên" }).click();
    await expect(lock).not.toBeVisible();
    await expect(page).toHaveURL(/\/admin\/users$/);
    await expect(page.getByText("Bệnh nhân thử nghiệm", { exact: true })).toBeVisible();
    expect(unlockCsrf).toBe("test-csrf");
    expect(lastAuthorization).toBe("Bearer unlocked-admin-token");
    if (flow === "expired access token") {
      await expect(page.getByPlaceholder("Tìm email hoặc họ tên")).toHaveValue("test");
    }
  });
}
