import axios from "axios";
import type { ApiResponse, FieldViolation } from "@/types/api";

export class ApiError extends Error {
  readonly code: string;
  readonly status?: number;
  readonly fields: FieldViolation[];

  constructor(message: string, code = "UNKNOWN_ERROR", status?: number, fields: FieldViolation[] = []) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.fields = fields;
  }
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;

  if (axios.isAxiosError<ApiResponse<never>>(error)) {
    const response = error.response?.data;
    if (response && typeof response.message === "string") {
      return new ApiError(
        response.message,
        response.code,
        error.response?.status,
        response.errors ?? [],
      );
    }

    if (!error.response) {
      return new ApiError(
        "Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối và thử lại.",
        "NETWORK_ERROR",
      );
    }
  }

  if (error instanceof Error) {
    return new ApiError(error.message);
  }

  return new ApiError("Đã xảy ra lỗi không xác định. Vui lòng thử lại.");
}

export function getApiErrorMessage(error: unknown): string {
  return toApiError(error).message;
}
