import { CircleDollarSign } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Card } from "@/components/ui/Card";
import { Pagination } from "@/components/ui/Pagination";
import { formatDateTime, formatMoney } from "@/utils/format";
import { usePatientPayments } from "../hooks/usePatientData";

export default function PaymentsPage() {
  const [page, setPage] = useState(0);
  const query = usePatientPayments(page);
  const visible = query.data?.content.filter((payment) => payment.status !== "DRAFT") ?? [];
  return <div><PageHeader title="Thanh toán" description="Theo dõi các phiếu chi phí đã hoàn tất khám. Phòng khám không hỗ trợ thanh toán trực tuyến." /><AsyncStateView loading={query.isLoading} error={query.error} empty={!visible.length} onRetry={() => void query.refetch()} emptyTitle="Chưa có phiếu thanh toán"><div className="grid gap-4 lg:grid-cols-2">{visible.map((payment) => <Link key={payment.id} to={`/patient/payments/${payment.id}`}><Card className="flex h-full items-start gap-4 p-5 transition hover:border-primary-200 hover:shadow-soft"><span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-primary-50 text-primary-700"><CircleDollarSign className="h-5 w-5" /></span><div className="min-w-0 flex-1"><div className="flex flex-wrap justify-between gap-2"><h2 className="font-bold text-ink">{payment.originalServiceName}</h2><StatusBadge status={payment.status} /></div><p className="mt-1 text-sm text-muted">{payment.doctorName} · {formatDateTime(payment.scheduledStartAt)}</p><p className="mt-3 text-lg font-extrabold text-primary-700">{formatMoney(payment.totalAmount)}</p></div></Card></Link>)}</div>{query.data && <div className="mt-6"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}</AsyncStateView></div>;
}
