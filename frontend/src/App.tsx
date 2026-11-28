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
  const { state, user, isLocked } = useAuth();
  const showChatbot = state === "authenticated" && user?.role === "PATIENT";
  return (
    <>
      <div inert={isLocked} aria-hidden={isLocked} className={isLocked ? "invisible" : undefined}>
        <AppRoutes />
        {showChatbot && <Suspense fallback={null}><ChatWidget /></Suspense>}
      </div>
      <SessionLockOverlay />
    </>
  );
}
