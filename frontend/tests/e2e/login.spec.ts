import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/api/auth/refresh", async (route) => {
    await route.fulfill({ status: 401, contentType: "application/json", body: JSON.stringify({ success: false, code: "AUTHENTICATION_REQUIRED", message: "Chưa đăng nhập", data: null }) });
  });
});

test("login responsive không tràn ngang và có đủ nội dung chính", async ({ page }, testInfo) => {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Chào mừng trở lại" })).toBeVisible();
  await expect(page.getByLabel(/Email/)).toBeVisible();
  await expect(page.getByLabel(/Mật khẩu/)).toBeVisible();
  const hasHorizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  expect(hasHorizontalOverflow).toBe(false);
  await page.screenshot({ path: testInfo.outputPath("login.png"), fullPage: true });
});

test("login thành công điều hướng bệnh nhân", async ({ page }) => {
  await page.route("**/api/auth/login", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ success: true, code: "SUCCESS", message: "Đăng nhập thành công", data: { accessToken: "access-token", tokenType: "Bearer", expiresInSeconds: 900, userId: "00000000-0000-0000-0000-000000000001", email: "patient@example.com", fullName: "Nguyễn Văn A", role: "PATIENT" } }) });
  });
  await page.goto("/login");
  await page.getByLabel(/Email/).fill("patient@example.com");
  await page.getByLabel(/Mật khẩu/).fill("Patient@123");
  await page.getByRole("button", { name: /Đăng nhập/ }).click();
  await expect(page).toHaveURL(/\/patient\/dashboard$/);
});
