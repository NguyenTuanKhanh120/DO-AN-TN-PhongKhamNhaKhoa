import { SessionLockOverlay } from "@/features/auth/components/SessionLockOverlay";
import { useSessionActivity } from "@/hooks/useSessionActivity";
import { AppRoutes } from "@/routes/AppRoutes";

export default function App() {
  useSessionActivity();
  return <><AppRoutes /><SessionLockOverlay /></>;
}
