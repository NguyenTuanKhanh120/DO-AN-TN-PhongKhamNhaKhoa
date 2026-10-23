import { History, Search } from "lucide-react";
import { useState } from "react";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Pagination } from "@/components/ui/Pagination";
import { Select } from "@/components/ui/Select";
import { Table, Td } from "@/components/ui/Table";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import type { Appointment, AppointmentStatus } from "@/types/domain";
import { actionLabels, appointmentStatusLabels, formatDateTime, roleLabels } from "@/utils/format";
import { useAdminAppointments, useAppointmentHistory } from "../hooks/useAdminData";

export default function AdminAppointmentsPage() {
  const [search, setSearch] = useState(""); const [status, setStatus] = useState<AppointmentStatus | "">(""); const [date, setDate] = useState(""); const [page, setPage] = useState(0); const [selected, setSelected] = useState<Appointment | null>(null);
  const query = useAdminAppointments({ search: useDebouncedValue(search), status: status || undefined, fromDate: date || undefined, toDate: date || undefined, page, size: 20 }); const history = useAppointmentHistory(selected?.id);
  return <div><PageHeader title="Quản lý lịch khám" description="Tra cứu lịch toàn hệ thống và xem audit history. Lịch sử chỉ đọc, không thể chỉnh sửa." /><div className="filter-bar mb-6"><Input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} icon={<Search className="h-4 w-4" />} placeholder="Tìm bệnh nhân, bác sĩ, dịch vụ" /><Select value={status} onChange={(event) => { setStatus(event.target.value as AppointmentStatus | ""); setPage(0); }} className="sm:w-52"><option value="">Tất cả trạng thái</option>{Object.entries(appointmentStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select><Input type="date" value={date} onChange={(event) => { setDate(event.target.value); setPage(0); }} className="sm:w-44" /></div><Card className="p-4 sm:p-5"><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length} onRetry={() => void query.refetch()}><Table headers={["Thời gian", "Bệnh nhân", "Bác sĩ", "Dịch vụ", "Trạng thái", ""]}>{query.data?.content.map((item) => <tr key={item.id}><Td className="whitespace-nowrap font-semibold">{formatDateTime(item.scheduledStartAt)}</Td><Td>{item.patientFullName}</Td><Td>{item.doctorFullName}</Td><Td>{item.serviceName}</Td><Td><StatusBadge status={item.status} /></Td><Td><Button size="sm" variant="ghost" onClick={() => setSelected(item)}><History className="h-4 w-4" /> Lịch sử</Button></Td></tr>)}</Table>{query.data && <div className="mt-5"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}</AsyncStateView></Card><Modal open={Boolean(selected)} onClose={() => setSelected(null)} title="Lịch sử thay đổi Appointment" description={selected ? `${selected.patientFullName} · ${formatDateTime(selected.scheduledStartAt)}` : undefined} className="max-w-2xl"><AsyncStateView loading={history.isLoading} error={history.error} empty={!history.data?.length}><ol className="relative ml-2 border-l border-slate-200 pl-6">{history.data?.map((item) => <li key={item.id} className="relative mb-6 last:mb-0"><span className="absolute -left-[31px] top-1 h-3 w-3 rounded-full bg-primary-700 ring-4 ring-primary-50" /><p className="font-bold text-ink">{actionLabels[item.action]}</p><p className="mt-1 text-sm text-muted">{item.changedByFullName} · {roleLabels[item.changedByRole]} · {formatDateTime(item.createdAt)}</p>{item.reason && <p className="mt-2 rounded-lg bg-slate-50 p-3 text-sm">Lý do: {item.reason}</p>}</li>)}</ol></AsyncStateView></Modal></div>;
}
