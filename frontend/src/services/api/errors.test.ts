import axios from "axios";
import { describe, expect, it } from "vitest";
import type { ApiResponse } from "@/types/api";
import { toApiError } from "./errors";

describe("toApiError", () => {
  it("giữ message và field errors cụ thể từ Backend", () => {
    const response: ApiResponse<never> = {
      success: false,
      code: "VALIDATION_FAILED",
      message: "Dữ liệu đầu vào không hợp lệ",
      data: null,
      errors: [{ field: "email", message: "Email không đúng định dạng" }],
      path: "/api/auth/login",
    };
    const axiosError = new axios.AxiosError("Bad request", "ERR_BAD_REQUEST", undefined, undefined, {
      data: response,
      status: 400,
      statusText: "Bad Request",
      headers: {},
      config: { headers: new axios.AxiosHeaders() },
    });
    const error = toApiError(axiosError);
    expect(error.message).toBe(response.message);
    expect(error.code).toBe(response.code);
    expect(error.fields).toEqual(response.errors);
  });
});
