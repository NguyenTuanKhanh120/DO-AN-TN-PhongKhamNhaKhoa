import { forwardRef, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/utils/cn";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  icon?: ReactNode;
  error?: boolean;
  suffix?: ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { className, icon, error = false, suffix, ...props },
  ref,
) {
  return (
    <div className="relative">
      {icon && <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">{icon}</span>}
      <input
        ref={ref}
        className={cn(
          "h-11 w-full rounded-xl border bg-white px-3.5 text-sm text-ink outline-none transition placeholder:text-slate-400 focus:border-primary-500 focus:ring-4 focus:ring-primary-100 disabled:cursor-not-allowed disabled:bg-slate-100",
          Boolean(icon) && "pl-10",
          Boolean(suffix) && "pr-11",
          error ? "border-red-400 focus:border-red-500 focus:ring-red-100" : "border-line",
          className,
        )}
        aria-invalid={error || undefined}
        {...props}
      />
      {suffix && <span className="absolute inset-y-0 right-0 flex items-center pr-3">{suffix}</span>}
    </div>
  );
});
