import { Search } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Input } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Select } from "@/components/ui/Select";
import { Table, Td } from "@/components/ui/Table";
import { useToast } from "@/contexts/ToastContext";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { getApiErrorMessage } from "@/services/api/errors";
import type { Appointment, AppointmentStatus } from "@/types/domain";
import { appointmentStatusLabels, formatDateTime } from "@/utils/format";
import { useDoctorAppointments, useMarkNoShow } from "../hooks/useDoctorData";

export default function DoctorAppointmentsPage() {
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<AppointmentStatus | "">("");
  const [page, setPage] = useState(0);
  const [noShowTarget, setNoShowTarget] = useState<Appointment | null>(null);
  const { showToast } = useToast();
  const query = useDoctorAppointments({ search: useDebouncedValue(search), status: status || undefined, page, size: 20 });
  const noShow = useMarkNoShow();
  const confirmNoShow = async () => {
    if (!noShowTarget) return;
    try { await noShow.mutateAsync(noShowTarget.id); showToast("Đã đánh dấu bệnh nhân không đến", "success"); setNoShowTarget(null); } catch (error) { showToast(getApiErrorMessage(error), "error"); }
  };
  return <div><PageHeader title="Lịch khám phụ trách" description="Danh sách lịch khám được phân công cho bạn." /><div className="filter-bar mb-6"><div className="flex-1"><Input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} icon={<Search className="h-4 w-4" />} placeholder="Tìm bệnh nhân hoặc dịch vụ" /></div><Select className="sm:w-52" value={status} onChange={(event) => { setStatus(event.target.value as AppointmentStatus | ""); setPage(0); }}><option value="">Tất cả trạng thái</option>{Object.entries(appointmentStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select></div><Card className="p-4 sm:p-5"><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length} onRetry={() => void query.refetch()} emptyTitle="Không có lịch phù hợp"><Table headers={["Thời gian", "Bệnh nhân", "Dịch vụ", "Trạng thái", "Thao tác"]}>{query.data?.content.map((item) => <tr key={item.id}><Td className="whitespace-nowrap font-semibold">{formatDateTime(item.scheduledStartAt)}</Td><Td>{item.patientFullName}<span className="block text-xs text-muted">{item.patientPhone}</span></Td><Td>{item.serviceName}</Td><Td><StatusBadge status={item.status} /></Td><Td><div className="flex items-center gap-2"><Link to={`/doctor/appointments/${item.id}`} state={{ appointment: item }} className="font-semibold text-primary-700 hover:underline">Chi tiết</Link>{item.status === "BOOKED" && <Button size="sm" variant="ghost" onClick={() => setNoShowTarget(item)}>Không đến</Button>}</div></Td></tr>)}</Table>{query.data && <div className="mt-5"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}</AsyncStateView></Card><ConfirmDialog open={Boolean(noShowTarget)} onClose={() => setNoShowTarget(null)} onConfirm={() => void confirmNoShow()} title="Đánh dấu không đến" description="Chỉ thực hiện sau giờ hẹn ít nhất 15 phút. Backend sẽ kiểm tra điều kiện này." confirmText="Xác nhận không đến" danger loading={noShow.isPending} /></div>;
}
