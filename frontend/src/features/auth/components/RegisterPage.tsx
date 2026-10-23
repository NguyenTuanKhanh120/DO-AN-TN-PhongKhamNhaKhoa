import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRight, LockKeyhole, Mail, UserRound } from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/components/ui/FormField";
import { Input } from "@/components/ui/Input";
import { PasswordInput } from "@/components/ui/PasswordInput";
import { useAuth } from "@/contexts/AuthContext";
import { toApiError } from "@/services/api/errors";
import { roleHome } from "@/routes/role-home";
import { AuthShell } from "./AuthShell";
import { registerSchema, type RegisterFormValues } from "../utils/validation";

export default function RegisterPage() {
  const { signUp } = useAuth();
  const navigate = useNavigate();
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema), defaultValues: { fullName: "", email: "", password: "", confirmPassword: "" } });
  const submit = handleSubmit(async ({ fullName, email, password }) => {
    try {
      const user = await signUp(fullName, email, password);
      navigate(roleHome[user.role], { replace: true });
    } catch (error) {
      const apiError = toApiError(error);
      apiError.fields.forEach((field) => {
        if (["fullName", "email", "password"].includes(field.field)) setError(field.field as "fullName" | "email" | "password", { message: field.message });
      });
      if (apiError.fields.length === 0) setError("root", { message: apiError.message });
    }
  });
  return (
    <AuthShell>
      <div className="mb-6"><h1 className="text-[1.7rem] font-extrabold tracking-tight text-ink">Tạo tài khoản bệnh nhân</h1><p className="mt-2 text-sm text-muted">Chủ động đặt lịch và theo dõi quá trình chăm sóc</p></div>
      <form className="space-y-3.5" onSubmit={submit} noValidate>
        {errors.root?.message && <div role="alert" className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">{errors.root.message}</div>}
        <FormField label="Họ và tên" required error={errors.fullName?.message}><Input placeholder="Nhập họ và tên" icon={<UserRound className="h-4 w-4" />} error={Boolean(errors.fullName)} {...register("fullName")} /></FormField>
        <FormField label="Email" required error={errors.email?.message}><Input type="email" autoComplete="email" placeholder="Nhập địa chỉ email" icon={<Mail className="h-4 w-4" />} error={Boolean(errors.email)} {...register("email")} /></FormField>
        <FormField label="Mật khẩu" required error={errors.password?.message} hint="Từ 8 đến 72 ký tự"><PasswordInput autoComplete="new-password" placeholder="Tạo mật khẩu" icon={<LockKeyhole className="h-4 w-4" />} error={Boolean(errors.password)} {...register("password")} /></FormField>
        <FormField label="Xác nhận mật khẩu" required error={errors.confirmPassword?.message}><PasswordInput autoComplete="new-password" placeholder="Nhập lại mật khẩu" icon={<LockKeyhole className="h-4 w-4" />} error={Boolean(errors.confirmPassword)} {...register("confirmPassword")} /></FormField>
        <Button type="submit" size="lg" className="mt-2 w-full" loading={isSubmitting}>Đăng ký <ArrowRight className="h-4 w-4" /></Button>
      </form>
      <p className="mt-5 text-center text-xs text-slate-500">Đã có tài khoản? <Link to="/login" className="font-bold text-primary-700">Đăng nhập</Link></p>
    </AuthShell>
  );
}
