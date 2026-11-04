import { lazy, Suspense } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { SessionLockOverlay } from "@/features/auth/components/SessionLockOverlay";
import { useSessionActivity } from "@/hooks/useSessionActivity";
import { AppRoutes } from "@/routes/AppRoutes";

const ChatWidget = lazy(() => import("@/features/chatbot").then((module) => ({
  default: module.ChatWidget,
})));

export default function App() {
  useSessionActivity();
  const { state, user } = useAuth();
  const showChatbot = state === "authenticated" && user?.role === "PATIENT";
  return <><AppRoutes />{showChatbot && <Suspense fallback={null}><ChatWidget /></Suspense>}<SessionLockOverlay /></>;
}
