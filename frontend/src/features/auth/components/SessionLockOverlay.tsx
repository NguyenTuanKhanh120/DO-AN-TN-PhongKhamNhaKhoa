import { zodResolver } from "@hookform/resolvers/zod";
import { LockKeyhole } from "lucide-react";
import { useLayoutEffect, useRef } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/components/ui/FormField";
import { PasswordInput } from "@/components/ui/PasswordInput";
import { useAuth } from "@/contexts/AuthContext";
import { getApiErrorMessage } from "@/services/api/errors";
import { unlockSchema, type UnlockFormValues } from "../utils/validation";

export function SessionLockOverlay() {
  const { isLocked, user, unlockSession, signOut } = useAuth();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<UnlockFormValues>({ resolver: zodResolver(unlockSchema) });
  useLayoutEffect(() => {
    const dialog = dialogRef.current;
    if (!isLocked || !dialog) return;
    dialog.showModal();
    return () => dialog.close();
  }, [isLocked]);
  if (!isLocked) return null;
  const submit = handleSubmit(async ({ password }) => {
    try {
      await unlockSession(password);
    } catch (error) {
      setError("password", { message: getApiErrorMessage(error) });
    }
  });
  return (
    <dialog ref={dialogRef} aria-modal="true" aria-labelledby="session-lock-title" onCancel={(event) => event.preventDefault()} onKeyDown={(event) => event.stopPropagation()} className="fixed inset-0 m-0 h-dvh max-h-none w-screen max-w-none border-0 bg-slate-950 p-5 open:grid open:place-items-center">
      <div className="w-full max-w-sm rounded-3xl bg-white p-7 text-center shadow-2xl">
        <span className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-primary-50 text-primary-700"><LockKeyhole className="h-7 w-7" /></span>
        <h2 id="session-lock-title" className="mt-5 text-2xl font-bold text-ink">Phiên làm việc đã khóa</h2>
        <p className="mt-2 text-sm text-muted">{user ? `Nhập lại mật khẩu của ${user.fullName} để tiếp tục an toàn.` : "Nhập lại mật khẩu để mở khóa phiên làm việc."}</p>
        <form className="mt-6 space-y-4 text-left" onSubmit={submit}>
          <FormField label="Mật khẩu" required error={errors.password?.message}>
            <PasswordInput icon={<LockKeyhole className="h-4 w-4" />} autoFocus error={Boolean(errors.password)} {...register("password")} />
          </FormField>
          <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>Mở khóa phiên</Button>
          <button type="button" onClick={() => void signOut()} className="w-full text-center text-sm font-semibold text-slate-500 hover:text-primary-700">Đăng xuất tài khoản</button>
        </form>
      </div>
    </dialog>
  );
}
