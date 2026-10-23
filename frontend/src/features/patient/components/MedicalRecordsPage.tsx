import { Stethoscope } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { Card } from "@/components/ui/Card";
import { Pagination } from "@/components/ui/Pagination";
import { formatDateTime } from "@/utils/format";
import { usePatientMedicalRecords } from "../hooks/usePatientData";

export default function MedicalRecordsPage() {
  const [page, setPage] = useState(0);
  const query = usePatientMedicalRecords(page);
  return <div><PageHeader title="Bệnh án của tôi" description="Kết quả khám và hướng dẫn điều trị từ bác sĩ phụ trách." /><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length} onRetry={() => void query.refetch()} emptyTitle="Chưa có bệnh án hoàn thành"><div className="grid gap-4 lg:grid-cols-2">{query.data?.content.map((record) => <Link key={record.id} to={`/patient/medical-records/${record.appointmentId}`}><Card className="h-full p-5 transition hover:border-primary-200 hover:shadow-soft"><div className="flex items-start gap-4"><span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-primary-50 text-primary-700"><Stethoscope className="h-5 w-5" /></span><div><h2 className="font-bold text-ink">{record.serviceName}</h2><p className="mt-1 text-sm text-muted">{record.doctorFullName}</p><p className="mt-1 text-sm text-muted">{formatDateTime(record.scheduledStartAt)}</p><p className="mt-3 line-clamp-2 text-sm text-slate-700"><strong>Chẩn đoán:</strong> {record.diagnosis}</p></div></div></Card></Link>)}</div>{query.data && <div className="mt-6"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}</AsyncStateView></div>;
}
