import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";
import type { ApiResponse } from "@/types/api";
import type { AuthResponse } from "@/types/domain";
import { ApiError, toApiError } from "./errors";
import { emitSessionEvent } from "./session-events";
import { tokenStore } from "./token-store";

const baseURL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8081";

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

function readCookie(name: string): string | undefined {
  const prefix = `${encodeURIComponent(name)}=`;
  return document.cookie
    .split(";")
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix))
    ?.slice(prefix.length);
}

function csrfHeaders(): Record<string, string> {
  const token = readCookie("XSRF-TOKEN");
  return token ? { "X-XSRF-TOKEN": decodeURIComponent(token) } : {};
}

const refreshClient = axios.create({ baseURL, withCredentials: true });

let refreshPromise: Promise<AuthResponse> | null = null;

async function performRefresh(): Promise<AuthResponse> {
  const response = await refreshClient.post<ApiResponse<AuthResponse>>(
    "/api/auth/refresh",
    undefined,
    { headers: csrfHeaders() },
  );
  if (!response.data.success || !response.data.data) {
    throw new ApiError(response.data.message, response.data.code, response.status, response.data.errors);
  }
  tokenStore.set(response.data.data.accessToken);
  return response.data.data;
}

async function refreshWithCrossTabLock(): Promise<AuthResponse> {
  if ("locks" in navigator) {
    return navigator.locks.request("nha-khoa-an-tam-refresh", performRefresh);
  }
  return performRefresh();
}

export function refreshAccessToken(): Promise<AuthResponse> {
  if (!refreshPromise) {
    refreshPromise = refreshWithCrossTabLock().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

export const apiClient = axios.create({
  baseURL,
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});

apiClient.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

function isRefreshableRequest(config: RetryableRequestConfig): boolean {
  const excluded = ["/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/unlock"];
  return !excluded.some((path) => config.url?.includes(path));
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<never>>) => {
    const status = error.response?.status;
    const config = error.config as RetryableRequestConfig | undefined;

    if (status === 423) {
      emitSessionEvent("locked");
      return Promise.reject(toApiError(error));
    }

    if (status === 401 && config && !config._retry && isRefreshableRequest(config)) {
      config._retry = true;
      try {
        const auth = await refreshAccessToken();
        config.headers.Authorization = `Bearer ${auth.accessToken}`;
        return apiClient.request(config);
      } catch (refreshError) {
        tokenStore.clear();
        emitSessionEvent("unauthorized");
        return Promise.reject(toApiError(refreshError));
      }
    }

    return Promise.reject(toApiError(error));
  },
);

export function getCsrfHeaders(): Record<string, string> {
  return csrfHeaders();
}

export function unwrap<T>(response: ApiResponse<T>): T {
  if (!response.success || response.data === null) {
    throw new ApiError(response.message, response.code, undefined, response.errors);
  }
  return response.data;
}
