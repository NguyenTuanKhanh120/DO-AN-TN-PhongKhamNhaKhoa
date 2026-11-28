import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { AuthResponse, Role } from "@/types/domain";
import { refreshAccessToken } from "@/services/api/client";
import { isSessionLockedError, toApiError } from "@/services/api/errors";
import { onSessionEvent } from "@/services/api/session-events";
import { tokenStore } from "@/services/api/token-store";
import * as authService from "@/features/auth/services/auth-service";

export type AuthState = "checking" | "authenticated" | "locked" | "unauthenticated";

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  role: Role;
}

interface AuthContextValue {
  state: AuthState;
  user: AuthUser | null;
  isLocked: boolean;
  signIn: (email: string, password: string) => Promise<AuthUser>;
  signUp: (fullName: string, phone: string, email: string, password: string) => Promise<AuthUser>;
  signOut: () => Promise<void>;
  unlockSession: (password: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function toUser(auth: AuthResponse): AuthUser {
  return { id: auth.userId, email: auth.email, fullName: auth.fullName, role: auth.role };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<AuthState>("checking");
  const [user, setUser] = useState<AuthUser | null>(null);
  const isLocked = state === "locked";

  const clearAuth = useCallback(() => {
    tokenStore.clear();
    setUser(null);
    setState("unauthenticated");
    queryClient.clear();
  }, [queryClient]);

  const acceptAuth = useCallback((auth: AuthResponse): AuthUser => {
    tokenStore.set(auth.accessToken);
    const nextUser = toUser(auth);
    setUser(nextUser);
    setState("authenticated");
    return nextUser;
  }, []);

  const lockAuth = useCallback(() => {
    tokenStore.clear();
    setState("locked");
  }, []);

  useEffect(() => {
    let active = true;
    refreshAccessToken()
      .then((auth) => {
        if (active) acceptAuth(auth);
      })
      .catch((error) => {
        if (!active) return;
        if (isSessionLockedError(error)) lockAuth();
        else clearAuth();
      });
    return () => {
      active = false;
    };
  }, [acceptAuth, clearAuth, lockAuth]);

  useEffect(() => {
    const removeLock = onSessionEvent("locked", lockAuth);
    const removeUnauthorized = onSessionEvent("unauthorized", clearAuth);
    return () => {
      removeLock();
      removeUnauthorized();
    };
  }, [clearAuth, lockAuth]);

  const signIn = useCallback(async (email: string, password: string) => {
    const auth = await authService.login({ email, password });
    queryClient.clear();
    return acceptAuth(auth);
  }, [acceptAuth, queryClient]);

  const signUp = useCallback(async (fullName: string, phone: string, email: string, password: string) => {
    const auth = await authService.registerPatient({ fullName, phone, email, password });
    queryClient.clear();
    return acceptAuth(auth);
  }, [acceptAuth, queryClient]);

  const signOut = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      clearAuth();
    }
  }, [clearAuth]);

  const unlockSession = useCallback(async (password: string) => {
    try {
      const auth = await authService.unlock(password);
      acceptAuth(auth);
      await queryClient.invalidateQueries({
        predicate: (query) => isSessionLockedError(query.state.error ?? query.state.fetchFailureReason),
      });
    } catch (error) {
      const apiError = toApiError(error);
      if ((apiError.status === 401 && apiError.code !== "INVALID_CREDENTIALS") || apiError.code === "ACCOUNT_INACTIVE") {
        clearAuth();
      }
      throw apiError;
    }
  }, [acceptAuth, clearAuth, queryClient]);

  const value = useMemo<AuthContextValue>(() => ({
    state,
    user,
    isLocked,
    signIn,
    signUp,
    signOut,
    unlockSession,
  }), [state, user, isLocked, signIn, signUp, signOut, unlockSession]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth phải được dùng bên trong AuthProvider");
  return context;
}
