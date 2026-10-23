import { Search, Star, Stethoscope } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { StatusBadge } from "@/components/common/StatusBadge";
import { PageHeader } from "@/components/common/PageHeader";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Select } from "@/components/ui/Select";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import type { DoctorStatus } from "@/types/domain";
import { useDoctors } from "../hooks/usePublicData";

export default function DoctorsPage() {
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<DoctorStatus | "">("ACTIVE");
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search);
  const query = useDoctors({ search: debouncedSearch, workingStatus: status || undefined, page, size: 12 });
  return (
    <div className="page-container py-10 sm:py-14">
      <PageHeader title="Đội ngũ bác sĩ" description="Tìm bác sĩ phù hợp với nhu cầu chăm sóc răng miệng của bạn." />
      <div className="filter-bar mb-6"><div className="flex-1"><Input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} placeholder="Tìm theo tên hoặc chuyên khoa" icon={<Search className="h-4 w-4" />} /></div><Select value={status} onChange={(event) => { setStatus(event.target.value as DoctorStatus | ""); setPage(0); }} className="sm:w-52"><option value="ACTIVE">Đang làm việc</option><option value="ON_LEAVE">Tạm nghỉ</option><option value="">Tất cả trạng thái</option></Select></div>
      <AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length} onRetry={() => void query.refetch()} emptyTitle="Chưa tìm thấy bác sĩ">
        <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">{query.data?.content.map((doctor) => <Link key={doctor.id} to={`/doctors/${doctor.id}`}><Card className="h-full p-5 transition hover:-translate-y-0.5 hover:border-primary-200 hover:shadow-soft"><div className="flex items-start gap-4"><span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-primary-50 text-primary-700"><Stethoscope className="h-6 w-6" /></span><div className="min-w-0 flex-1"><h2 className="truncate font-bold text-ink">{doctor.fullName}</h2><p className="mt-1 text-sm text-muted">{doctor.specialization}</p><div className="mt-3 flex flex-wrap items-center gap-2"><StatusBadge status={doctor.workingStatus} /><span className="flex items-center gap-1 text-xs text-amber-600"><Star className="h-3.5 w-3.5 fill-current" /> {doctor.rating.toFixed(1)}</span></div><p className="mt-3 text-sm text-slate-600">{doctor.experienceYears} năm kinh nghiệm</p></div></div></Card></Link>)}</div>
        {query.data && <div className="mt-6"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}
      </AsyncStateView>
    </div>
  );
}
