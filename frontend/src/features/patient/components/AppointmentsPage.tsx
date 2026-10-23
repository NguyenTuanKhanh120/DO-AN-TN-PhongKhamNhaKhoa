import { Search } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Select } from "@/components/ui/Select";
import { Table, Td } from "@/components/ui/Table";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import type { AppointmentStatus } from "@/types/domain";
import { appointmentStatusLabels, formatDateTime } from "@/utils/format";
import { usePatientAppointments } from "../hooks/usePatientData";

export default function AppointmentsPage() {
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<AppointmentStatus | "">("");
  const [page, setPage] = useState(0);
  const query = usePatientAppointments({ search: useDebouncedValue(search), status: status || undefined, page, size: 20 });
  return <div><PageHeader title="Lịch khám của tôi" description="Theo dõi trạng thái, hủy hoặc đổi lịch khi còn trong thời hạn cho phép." /><div className="filter-bar mb-6"><div className="flex-1"><Input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} placeholder="Tìm bác sĩ hoặc dịch vụ" icon={<Search className="h-4 w-4" />} /></div><Select className="sm:w-52" value={status} onChange={(event) => { setStatus(event.target.value as AppointmentStatus | ""); setPage(0); }}><option value="">Tất cả trạng thái</option>{Object.entries(appointmentStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select></div><Card className="p-4 sm:p-5"><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length} onRetry={() => void query.refetch()} emptyTitle="Chưa có lịch khám"><Table headers={["Thời gian", "Dịch vụ", "Bác sĩ", "Trạng thái", ""]}>{query.data?.content.map((item) => <tr key={item.id}><Td className="whitespace-nowrap font-semibold text-ink">{formatDateTime(item.scheduledStartAt)}</Td><Td>{item.serviceName}</Td><Td>{item.doctorFullName}</Td><Td><StatusBadge status={item.status} /></Td><Td className="text-right"><Link to={`/patient/appointments/${item.id}`} className="font-semibold text-primary-700 hover:underline">Chi tiết</Link></Td></tr>)}</Table>{query.data && <div className="mt-5"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}</AsyncStateView></Card></div>;
}
