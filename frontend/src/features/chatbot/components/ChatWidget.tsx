import { Bot, MessageCircle, X } from "lucide-react";
import { useState } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { ChatWindow } from "./ChatWindow";

export function ChatWidget() {
  const { state, user } = useAuth();
  const [open, setOpen] = useState(false);
  const [hasOpened, setHasOpened] = useState(false);

  if (state !== "authenticated" || user?.role !== "PATIENT") return null;

  const toggle = () => {
    setHasOpened(true);
    setOpen((value) => !value);
  };

  return (
    <>
      {hasOpened && <ChatWindow open={open} onClose={() => setOpen(false)} />}
      <button
        type="button"
        onClick={toggle}
        className="fixed bottom-4 right-4 z-40 inline-flex h-14 items-center gap-2 rounded-2xl bg-primary-700 px-4 font-bold text-white shadow-xl transition hover:-translate-y-0.5 hover:bg-primary-800 focus:outline-none focus-visible:ring-4 focus-visible:ring-primary-200 sm:bottom-6 sm:right-6"
        aria-label={open ? "Đóng trợ lý nha khoa" : "Mở trợ lý nha khoa"}
        aria-expanded={open}
      >
        {open ? <X className="h-5 w-5" /> : <MessageCircle className="h-5 w-5" />}
        <span className="hidden sm:inline">{open ? "Đóng" : "Hỏi trợ lý AI"}</span>
        {!open && <Bot className="hidden h-4 w-4 sm:block" />}
      </button>
    </>
  );
}
