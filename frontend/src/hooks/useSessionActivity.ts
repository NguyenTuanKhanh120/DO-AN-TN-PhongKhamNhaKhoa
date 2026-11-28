import { useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { getSession, recordActivity } from "@/features/auth/services/auth-service";
import { emitSessionEvent } from "@/services/api/session-events";
import type { AuthSession } from "@/types/domain";

const ACTIVITY_INTERVAL_MS = 60_000;
const SESSION_RETRY_MS = 5_000;

export function useSessionActivity(): void {
  const { state, user, isLocked } = useAuth();

  useEffect(() => {
    if (state !== "authenticated" || !user || user.role === "PATIENT" || isLocked) return;

    let disposed = false;
    let locked = false;
    let lastSentAt = 0;
    let lastActivityAt = 0;
    let idleDeadline: number | null = null;
    let idleTimer: ReturnType<typeof setTimeout> | undefined;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let activityPending = false;

    const lock = () => {
      if (disposed || locked) return;
      locked = true;
      clearTimeout(idleTimer);
      clearTimeout(retryTimer);
      emitSessionEvent("locked");
    };

    const checkDeadline = () => {
      if (idleDeadline !== null && Date.now() >= idleDeadline) lock();
      return locked;
    };

    const acceptSession = (session: AuthSession) => {
      if (disposed || locked) return;
      if (session.status === "LOCKED") {
        lock();
        return;
      }
      clearTimeout(idleTimer);
      clearTimeout(retryTimer);
      if (!session.idleLockEnabled || session.idleTimeoutSeconds === null) {
        idleDeadline = null;
        return;
      }
      // Out-of-order responses must not replace a more recent activity acknowledgement.
      lastActivityAt = Math.max(lastActivityAt, Date.parse(session.lastActivityAt));
      idleDeadline = lastActivityAt + session.idleTimeoutSeconds * 1_000;
      if (!checkDeadline()) idleTimer = setTimeout(checkDeadline, idleDeadline - Date.now());
    };

    const loadSession = () => {
      void getSession().then(acceptSession).catch(() => {
        if (!disposed && !locked) retryTimer = setTimeout(loadSession, SESSION_RETRY_MS);
      });
    };

    const sendActivity = (event: Event) => {
      if (checkDeadline()) {
        event.preventDefault();
        event.stopImmediatePropagation();
        return;
      }
      const now = Date.now();
      if (activityPending || now - lastSentAt < ACTIVITY_INTERVAL_MS) return;
      lastSentAt = now;
      activityPending = true;
      void recordActivity().then(acceptSession).catch(() => undefined).finally(() => {
        activityPending = false;
      });
    };

    const checkOnVisibility = () => {
      if (document.visibilityState === "visible") checkDeadline();
    };

    loadSession();
    window.addEventListener("pointerdown", sendActivity, true);
    window.addEventListener("keydown", sendActivity, true);
    window.addEventListener("focus", checkDeadline);
    window.addEventListener("pageshow", checkDeadline);
    document.addEventListener("visibilitychange", checkOnVisibility);
    return () => {
      disposed = true;
      clearTimeout(idleTimer);
      clearTimeout(retryTimer);
      window.removeEventListener("pointerdown", sendActivity, true);
      window.removeEventListener("keydown", sendActivity, true);
      window.removeEventListener("focus", checkDeadline);
      window.removeEventListener("pageshow", checkDeadline);
      document.removeEventListener("visibilitychange", checkOnVisibility);
    };
  }, [state, user, isLocked]);
}
