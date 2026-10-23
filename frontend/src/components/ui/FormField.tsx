import type { ReactNode } from "react";

export function FormField({ label, required, error, hint, children }: {
  label: string;
  required?: boolean;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <label className="block space-y-1.5 text-sm font-medium text-ink">
      <span>{label}{required && <span className="text-red-600"> *</span>}</span>
      {children}
      {error ? <span className="block text-xs font-normal text-red-600">{error}</span> : hint ? <span className="block text-xs font-normal text-slate-500">{hint}</span> : null}
    </label>
  );
}
