import { Search } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { formatDateTime, formatMoney } from "@/utils/format";
import { useStaffPayments } from "../hooks/useStaffData";

export default function StaffPaymentsPage() {
  const [tab, setTab] = useState<"UNPAID" | "PAID">("UNPAID"); const [search, setSearch] = useState(""); const [page, setPage] = useState(0);
  const query = useStaffPayments({ status: tab, search: useDebouncedValue(search), page, size: 20 });
  return <div><PageHeader title="Thu tiền tại phòng khám" description="Kiểm tra phiếu và xác nhận tiền mặt hoặc chuyển khoản đã nhận trực tiếp." /><div className="mb-4 flex gap-2 rounded-xl bg-slate-100 p-1 sm:w-fit">{(["UNPAID", "PAID"] as const).map((value) => <button key={value} onClick={() => { setTab(value); setPage(0); }} className={`rounded-lg px-4 py-2 text-sm font-semibold ${tab === value ? "bg-white text-primary-700 shadow" : "text-slate-600"}`}>{value === "UNPAID" ? "Chưa thanh toán" : "Đã thanh toán"}</button>)}</div><div className="filter-bar mb-6"><Input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} placeholder="Tìm bệnh nhân hoặc dịch vụ" icon={<Search className="h-4 w-4" />} /></div><Card className="p-4 sm:p-5"><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length} onRetry={() => void query.refetch()} emptyTitle={tab === "UNPAID" ? "Không có phiếu chờ thu" : "Chưa có phiếu đã thanh toán"}><div className="space-y-3">{query.data?.content.map((item) => <Link key={item.id} to={`/staff/payments/${item.id}`} className="flex flex-col justify-between gap-3 rounded-xl border border-slate-100 p-4 hover:border-primary-200 sm:flex-row sm:items-center"><div><p className="font-semibold text-ink">{item.patientName} · {item.originalServiceName}</p><p className="mt-1 text-sm text-muted">{item.doctorName} · {formatDateTime(item.scheduledStartAt)}</p></div><div className="flex items-center gap-4"><strong className="text-primary-700">{formatMoney(item.totalAmount)}</strong><StatusBadge status={item.status} /></div></Link>)}</div>{query.data && <div className="mt-5"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}</AsyncStateView></Card></div>;
}
