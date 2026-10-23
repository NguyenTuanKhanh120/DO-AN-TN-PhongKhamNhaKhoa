import { useEffect, useRef } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { recordActivity } from "@/features/auth/services/auth-service";

const ACTIVITY_INTERVAL_MS = 60_000;

export function useSessionActivity(): void {
  const { state, user, isLocked } = useAuth();
  const lastSentAt = useRef(0);

  useEffect(() => {
    if (state !== "authenticated" || !user || user.role === "PATIENT" || isLocked) return;

    const sendActivity = () => {
      const now = Date.now();
      if (now - lastSentAt.current < ACTIVITY_INTERVAL_MS) return;
      lastSentAt.current = now;
      void recordActivity().catch(() => undefined);
    };

    window.addEventListener("pointerdown", sendActivity, { passive: true });
    window.addEventListener("keydown", sendActivity);
    return () => {
      window.removeEventListener("pointerdown", sendActivity);
      window.removeEventListener("keydown", sendActivity);
    };
  }, [state, user, isLocked]);
}
