import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";
import type { Role } from "@/types/domain";
import { roleHome } from "./role-home";

function CheckingSession() {
  return <div className="grid min-h-screen place-items-center bg-slate-50"><div className="text-center"><span className="mx-auto block h-10 w-10 animate-spin rounded-full border-4 border-primary-100 border-t-primary-700" /><p className="mt-3 text-sm text-muted">Đang kiểm tra phiên đăng nhập…</p></div></div>;
}

export function ProtectedRoute({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { state, user } = useAuth();
  const location = useLocation();
  if (state === "checking") return <CheckingSession />;
  if (state !== "authenticated" || !user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!roles.includes(user.role)) return <Navigate to="/forbidden" replace />;
  return <>{children}</>;
}

export function PublicOnlyRoute({ children }: { children: ReactNode }) {
  const { state, user } = useAuth();
  if (state === "checking") return <CheckingSession />;
  if (state === "authenticated" && user) return <Navigate to={roleHome[user.role]} replace />;
  return <>{children}</>;
}
