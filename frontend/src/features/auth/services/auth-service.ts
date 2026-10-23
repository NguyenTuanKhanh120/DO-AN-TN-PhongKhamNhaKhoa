import type { ApiResponse } from "@/types/api";
import type { AuthResponse, AuthSession } from "@/types/domain";
import { apiClient, getCsrfHeaders, unwrap } from "@/services/api/client";

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload extends LoginPayload {
  fullName: string;
}

export async function login(payload: LoginPayload): Promise<AuthResponse> {
  const { data } = await apiClient.post<ApiResponse<AuthResponse>>("/api/auth/login", payload);
  return unwrap(data);
}

export async function registerPatient(payload: RegisterPayload): Promise<AuthResponse> {
  const { data } = await apiClient.post<ApiResponse<AuthResponse>>("/api/auth/register", payload);
  return unwrap(data);
}

export async function logout(): Promise<void> {
  await apiClient.post<ApiResponse<null>>("/api/auth/logout", undefined, { headers: getCsrfHeaders() });
}

export async function unlock(password: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<ApiResponse<AuthResponse>>(
    "/api/auth/unlock",
    { password },
    { headers: getCsrfHeaders() },
  );
  return unwrap(data);
}

export async function getSession(): Promise<AuthSession> {
  const { data } = await apiClient.get<ApiResponse<AuthSession>>("/api/auth/session");
  return unwrap(data);
}

export async function recordActivity(): Promise<AuthSession> {
  const { data } = await apiClient.post<ApiResponse<AuthSession>>("/api/auth/activity");
  return unwrap(data);
}
