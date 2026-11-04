import { expect, test } from "@playwright/test";

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
