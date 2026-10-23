import { CalendarDays, Clock3, Stethoscope, UsersRound } from "lucide-react";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { formatDateTime } from "@/utils/format";
import { useTodayAppointments } from "../hooks/useDoctorData";

export default function DoctorDashboardPage() {
  const query = useTodayAppointments({ page: 0, size: 20 });
  const appointments = query.data?.content ?? [];
  const stats = [
    { label: "Lịch hôm nay", value: appointments.length, icon: CalendarDays },
    { label: "Chờ khám", value: appointments.filter((item) => item.status === "BOOKED").length, icon: UsersRound },
    { label: "Đang khám", value: appointments.filter((item) => item.status === "IN_PROGRESS").length, icon: Stethoscope },
    { label: "Đã hoàn thành", value: appointments.filter((item) => item.status === "COMPLETED").length, icon: Clock3 },
  ];
  return <div><PageHeader title="Lịch khám hôm nay" description="Bắt đầu khám trực tiếp từ trạng thái đã đặt lịch; hệ thống không có bước check-in." action={<Link to="/doctor/schedules"><Button variant="outline"><CalendarDays className="h-4 w-4" /> Lịch làm việc tuần</Button></Link>} /><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{stats.map(({ label, value, icon: Icon }) => <Card key={label} className="p-5"><div className="flex items-center justify-between"><span className="grid h-10 w-10 place-items-center rounded-xl bg-primary-50 text-primary-700"><Icon className="h-5 w-5" /></span><strong className="text-2xl text-ink">{value}</strong></div><p className="mt-3 text-sm font-semibold text-muted">{label}</p></Card>)}</div><Card className="mt-6 p-5"><h2 className="mb-4 text-lg font-bold text-ink">Danh sách bệnh nhân</h2><AsyncStateView loading={query.isLoading} error={query.error} empty={!appointments.length} onRetry={() => void query.refetch()} emptyTitle="Hôm nay chưa có lịch khám"><div className="space-y-3">{appointments.map((item) => <Link key={item.id} to={`/doctor/appointments/${item.id}`} state={{ appointment: item }} className="flex flex-col justify-between gap-3 rounded-xl border border-slate-100 p-4 hover:border-primary-200 sm:flex-row sm:items-center"><div><p className="font-semibold text-ink">{item.patientFullName} · {item.serviceName}</p><p className="mt-1 text-sm text-muted">{formatDateTime(item.scheduledStartAt)}{item.patientPhone ? ` · ${item.patientPhone}` : ""}</p></div><StatusBadge status={item.status} /></Link>)}</div></AsyncStateView></Card></div>;
}
