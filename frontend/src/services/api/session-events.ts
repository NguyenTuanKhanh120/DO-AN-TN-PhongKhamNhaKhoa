type SessionEvent = "locked" | "unauthorized";
type SessionListener = () => void;

const listeners: Record<SessionEvent, Set<SessionListener>> = {
  locked: new Set(),
  unauthorized: new Set(),
};

export function emitSessionEvent(event: SessionEvent): void {
  listeners[event].forEach((listener) => listener());
}

export function onSessionEvent(event: SessionEvent, listener: SessionListener): () => void {
  listeners[event].add(listener);
  return () => listeners[event].delete(listener);
}
