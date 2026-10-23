import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { AuthResponse, Role } from "@/types/domain";
import { refreshAccessToken } from "@/services/api/client";
import { onSessionEvent } from "@/services/api/session-events";
import { tokenStore } from "@/services/api/token-store";
import * as authService from "@/features/auth/services/auth-service";

export type AuthState = "checking" | "authenticated" | "unauthenticated";

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
  signUp: (fullName: string, email: string, password: string) => Promise<AuthUser>;
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
  const [isLocked, setIsLocked] = useState(false);

  const clearAuth = useCallback(() => {
    tokenStore.clear();
    setUser(null);
    setIsLocked(false);
    setState("unauthenticated");
    queryClient.clear();
  }, [queryClient]);

  const acceptAuth = useCallback((auth: AuthResponse): AuthUser => {
    tokenStore.set(auth.accessToken);
    const nextUser = toUser(auth);
    setUser(nextUser);
    setState("authenticated");
    setIsLocked(false);
    return nextUser;
  }, []);

  useEffect(() => {
    let active = true;
    refreshAccessToken()
      .then((auth) => {
        if (active) acceptAuth(auth);
      })
      .catch(() => {
        if (active) clearAuth();
      });
    return () => {
      active = false;
    };
  }, [acceptAuth, clearAuth]);

  useEffect(() => {
    const removeLock = onSessionEvent("locked", () => setIsLocked(true));
    const removeUnauthorized = onSessionEvent("unauthorized", clearAuth);
    return () => {
      removeLock();
      removeUnauthorized();
    };
  }, [clearAuth]);

  const signIn = useCallback(async (email: string, password: string) => {
    const auth = await authService.login({ email, password });
    queryClient.clear();
    return acceptAuth(auth);
  }, [acceptAuth, queryClient]);

  const signUp = useCallback(async (fullName: string, email: string, password: string) => {
    const auth = await authService.registerPatient({ fullName, email, password });
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
    const auth = await authService.unlock(password);
    acceptAuth(auth);
  }, [acceptAuth]);

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
