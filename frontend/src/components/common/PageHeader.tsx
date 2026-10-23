import type { ReactNode } from "react";

export function PageHeader({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return <div className="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-start"><div><h1 className="page-heading">{title}</h1>{description && <p className="page-subtitle">{description}</p>}</div>{action}</div>;
}
