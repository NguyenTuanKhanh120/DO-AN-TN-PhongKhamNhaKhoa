/// <reference types="node" />
import { expect, test } from "@playwright/test";
import { createServer } from "node:http";
import type { AddressInfo } from "node:net";

const patientAuth = {
  accessToken: "access-token",
  tokenType: "Bearer",
  expiresInSeconds: 900,
  userId: "00000000-0000-0000-0000-000000000001",
  email: "patient@example.com",
  fullName: "Nguyễn Văn A",
  role: "PATIENT",
};

const emptyPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
};

test("bệnh nhân mở chatbot và nhận trọn vẹn phản hồi SSE", async ({ page }) => {
  let completed = false;
  let streamAuthorization: string | undefined;
  let streamPayload: unknown;

  await page.route("**/api/auth/refresh", (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ success: true, code: "SUCCESS", message: "Thành công", data: patientAuth }),
  }));
  await page.route("**/api/patient/appointments**", (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ success: true, code: "SUCCESS", message: "Thành công", data: emptyPage }),
  }));
  await page.route("**/api/chat/session", (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({
      success: true,
      code: "SUCCESS",
      message: "Thành công",
      data: {
        id: "00000000-0000-0000-0000-000000000010",
        title: "Tư vấn nha khoa",
        createdAt: "2026-10-30T01:00:00Z",
        updatedAt: "2026-10-30T01:00:00Z",
      },
    }),
  }));
  await page.route("**/api/chat/messages**", async (route) => {
    if (route.request().method() !== "GET") {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        success: true,
        code: "SUCCESS",
        message: "Thành công",
        data: completed ? {
          ...emptyPage,
          content: [
            {
              id: "00000000-0000-0000-0000-000000000011",
              sender: "USER",
              content: "Tôi bị đau răng",
              metadata: {},
              createdAt: "2026-10-30T01:00:00Z",
            },
            {
              id: "00000000-0000-0000-0000-000000000012",
              sender: "ASSISTANT",
              content: "Bạn nên đến nha sĩ để được kiểm tra trực tiếp.",
              metadata: {
                intents: ["SYMPTOM_CONSULTATION"],
                sources: [],
                suggestedServices: [],
                suggestedDoctors: [],
                suggestedSlots: [],
                needsClarification: false,
              },
              createdAt: "2026-10-30T01:00:01Z",
            },
          ],
          totalElements: 2,
          totalPages: 1,
        } : emptyPage,
      }),
    });
  });
  await page.route("**/api/chat/messages/stream", async (route) => {
    const request = route.request();
    streamAuthorization = request.headers().authorization;
    streamPayload = request.postDataJSON();
    completed = true;
    const body = [
      "event: started\ndata: {\"userMessageId\":\"00000000-0000-0000-0000-000000000011\",\"startedAt\":\"2026-10-30T01:00:00Z\"}\n\n",
      "event: token\ndata: {\"content\":\"Bạn nên đến nha sĩ để được kiểm tra trực tiếp.\"}\n\n",
      "event: metadata\ndata: {\"intents\":[\"SYMPTOM_CONSULTATION\"],\"sources\":[],\"suggestedServices\":[],\"suggestedDoctors\":[],\"suggestedSlots\":[],\"needsClarification\":false}\n\n",
      "event: completed\ndata: {\"messageId\":\"00000000-0000-0000-0000-000000000012\",\"createdAt\":\"2026-10-30T01:00:01Z\"}\n\n",
    ].join("");
    await route.fulfill({ status: 200, contentType: "text/event-stream", body });
  });

  await page.goto("/patient/dashboard");
  await page.getByRole("button", { name: "Mở trợ lý nha khoa" }).click();
  await expect(page.getByRole("dialog", { name: "Trợ lý nha khoa An Tâm" })).toBeVisible();
  await page.getByLabel("Nội dung trò chuyện").fill("Tôi bị đau răng");
  await page.getByRole("button", { name: "Gửi tin nhắn" }).click();

  await expect(page.getByText("Tôi bị đau răng", { exact: true })).toBeVisible();
  await expect(page.getByText("Bạn nên đến nha sĩ để được kiểm tra trực tiếp.", { exact: true })).toBeVisible();
  await expect(page.getByText(/không thay thế chẩn đoán hoặc điều trị/)).toBeVisible();
  expect(streamAuthorization).toBe("Bearer access-token");
  expect(streamPayload).toEqual({ message: "Tôi bị đau răng" });
});

test("đóng chatbot ngắt SSE sau header, mở lại gửi được câu mới", async ({ page }) => {
  let disconnected = false;
  let requests = 0;
  let completed = false;
  const answer = "Khám răng tổng quát có giá 200.000 đ.";
  const server = createServer((request, response) => {
    response.setHeader("Access-Control-Allow-Origin", "http://127.0.0.1:5173");
    response.setHeader("Access-Control-Allow-Credentials", "true");
    response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, X-XSRF-TOKEN");
    response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    if (request.method === "OPTIONS") {
      response.writeHead(204).end();
      return;
    }
    requests++;
    response.writeHead(200, { "Content-Type": "text/event-stream", "Cache-Control": "no-cache" });
    response.flushHeaders();
    response.write('event: started\ndata: {"userMessageId":"user-id","startedAt":"2026-11-26T01:00:00Z"}\n\n');
    if (requests === 1) {
      response.write('event: token\ndata: {"content":"Phản hồi đang nhận"}\n\n');
      // Keep a real network body open until the browser cancels it.
      response.on("close", () => { disconnected = !response.writableEnded; });
      return;
    }
    completed = true;
    response.write(`event: token\ndata: ${JSON.stringify({ content: answer })}\n\n`);
    response.end('event: completed\ndata: {"messageId":"assistant-id","createdAt":"2026-11-26T01:00:01Z"}\n\n');
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const streamUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}/stream`;
  try {
    // Only the SSE transport uses the local streaming fixture; other APIs remain mocked.
    await page.addInitScript((url) => {
      const fetchRequest = window.fetch.bind(window);
      window.fetch = (input, init) => typeof input === "string" && input.endsWith("/api/chat/messages/stream")
        ? fetchRequest(url, init)
        : fetchRequest(input, init);
    }, streamUrl);
    const envelope = (data: unknown) => ({ success: true, code: "SUCCESS", message: "Thành công", data });
    await page.route("**/api/auth/refresh", (route) => route.fulfill({ json: envelope(patientAuth) }));
    await page.route("**/api/patient/appointments**", (route) => route.fulfill({ json: envelope(emptyPage) }));
    await page.route("**/api/chat/session", (route) => route.fulfill({ json: envelope({
      id: "session-id", title: "Tư vấn", createdAt: "2026-11-26T01:00:00Z", updatedAt: "2026-11-26T01:00:00Z",
    }) }));
    await page.route("**/api/chat/messages**", (route) => route.fulfill({ json: envelope({
      ...emptyPage,
      content: completed ? [{ id: "assistant-id", sender: "ASSISTANT", content: answer, metadata: {}, createdAt: "2026-11-26T01:00:01Z" }] : [],
      totalElements: completed ? 1 : 0,
    }) }));

    await page.goto("/patient/dashboard");
    await page.getByRole("button", { name: "Mở trợ lý nha khoa" }).click();
    await page.getByLabel("Nội dung trò chuyện").fill("Bác sĩ nào phù hợp?");
    await page.getByRole("button", { name: "Gửi tin nhắn" }).click();
    await expect(page.getByText("Phản hồi đang nhận", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Nội dung trò chuyện")).toBeDisabled();
    await page.getByRole("button", { name: "Đóng chatbot", exact: true }).click();
    await expect.poll(() => disconnected).toBe(true);
    expect(requests).toBe(1);

    await page.getByRole("button", { name: "Mở trợ lý nha khoa" }).click();
    await expect(page.getByLabel("Nội dung trò chuyện")).toBeEnabled();
    await expect(page.getByText("Phản hồi đang nhận", { exact: true })).toHaveCount(0);
    await expect(page.getByText("hệ thống lỗi vui lòng thử lại sau", { exact: true })).toHaveCount(0);
    await page.getByLabel("Nội dung trò chuyện").fill("Khám răng tổng quát giá bao nhiêu?");
    await page.getByRole("button", { name: "Gửi tin nhắn" }).click();
    await expect(page.getByText(answer, { exact: true })).toBeVisible();
    await expect(page.getByLabel("Nội dung trò chuyện")).toBeEnabled();
    expect(requests).toBe(2);
  } finally {
    server.closeAllConnections();
    await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
});
