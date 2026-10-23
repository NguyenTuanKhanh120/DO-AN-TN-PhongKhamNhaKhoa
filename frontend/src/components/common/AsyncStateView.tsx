import { AlertTriangle, Inbox, RefreshCcw } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { getApiErrorMessage } from "@/services/api/errors";

export function SkeletonList({ rows = 4 }: { rows?: number }) {
  return <div className="space-y-3" aria-label="Đang tải">{Array.from({ length: rows }, (_, index) => <div key={index} className="h-16 animate-pulse rounded-xl bg-slate-100" />)}</div>;
}

export function EmptyState({ title = "Chưa có dữ liệu", description = "Dữ liệu sẽ xuất hiện tại đây khi có thông tin phù hợp.", action }: {
  title?: string;
  description?: string;
  action?: ReactNode;
}) {
  return <div className="flex min-h-48 flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center"><Inbox className="mb-3 h-9 w-9 text-slate-400" /><h3 className="font-semibold text-ink">{title}</h3><p className="mt-1 max-w-md text-sm text-muted">{description}</p>{action && <div className="mt-4">{action}</div>}</div>;
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return <div className="flex min-h-48 flex-col items-center justify-center rounded-xl border border-red-100 bg-red-50 p-8 text-center"><AlertTriangle className="mb-3 h-9 w-9 text-red-500" /><h3 className="font-semibold text-red-800">Không thể tải dữ liệu</h3><p className="mt-1 max-w-md text-sm text-red-700">{getApiErrorMessage(error)}</p>{onRetry && <Button className="mt-4" variant="outline" onClick={onRetry}><RefreshCcw className="h-4 w-4" /> Thử lại</Button>}</div>;
}

export function AsyncStateView({ loading, error, empty, onRetry, emptyTitle, emptyDescription, children }: {
  loading: boolean;
  error?: unknown;
  empty?: boolean;
  onRetry?: () => void;
  emptyTitle?: string;
  emptyDescription?: string;
  children: ReactNode;
}) {
  if (loading) return <SkeletonList />;
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (empty) return <EmptyState title={emptyTitle} description={emptyDescription} />;
  return <>{children}</>;
}
