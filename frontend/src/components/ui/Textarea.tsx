import { forwardRef, type TextareaHTMLAttributes } from "react";
import { cn } from "@/utils/cn";

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  error?: boolean;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { className, error = false, ...props },
  ref,
) {
  return (
    <textarea
      ref={ref}
      className={cn(
        "min-h-24 w-full resize-y rounded-xl border bg-white px-3.5 py-3 text-sm text-ink outline-none transition placeholder:text-slate-400 focus:ring-4 disabled:bg-slate-100",
        error ? "border-red-400 focus:border-red-500 focus:ring-red-100" : "border-line focus:border-primary-500 focus:ring-primary-100",
        className,
      )}
      aria-invalid={error || undefined}
      {...props}
    />
  );
});
