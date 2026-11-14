import { CalendarDays, Clock3, Stethoscope, UsersRound } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { useToast } from "@/contexts/ToastContext";
import type { Appointment, AppointmentStatus } from "@/types/domain";
import { clinicToday } from "@/utils/date";
import { formatAppointmentSchedule } from "@/utils/format";
import { getApiErrorMessage } from "@/services/api/errors";
import { useDoctorWeeklyAppointments, useStartExamination } from "../hooks/useDoctorData";

const categories = [
  { id: "waiting", label: "Chờ khám", statuses: ["BOOKED", "CHECKED_IN"] },
  { id: "progress", label: "Đang khám", statuses: ["IN_PROGRESS"] },
  { id: "completed", label: "Hoàn thành", statuses: ["COMPLETED"] },
  { id: "other", label: "Không đến / Đã hủy", statuses: ["NO_SHOW", "CANCELLED"] },
] satisfies Array<{ id: string; label: string; statuses: AppointmentStatus[] }>;

export default function DoctorDashboardPage() {
  const today = clinicToday();
  const query = useDoctorWeeklyAppointments(today, today);
  const [category, setCategory] = useState("waiting");
  const navigate = useNavigate();
  const { showToast } = useToast();
  const start = useStartExamination();
  const appointments = [...(query.data ?? [])].sort((left, right) =>
    (left.session === "AFTERNOON" ? 1 : 0) - (right.session === "AFTERNOON" ? 1 : 0)
    || (left.queueNumber ?? 0) - (right.queueNumber ?? 0)
    || left.estimatedStartAt.localeCompare(right.estimatedStartAt)
    || left.id.localeCompare(right.id),
  );
  const activeCategory = categories.find((item) => item.id === category);
  const visibleAppointments = appointments.filter((item) =>
    activeCategory?.statuses.some((status) => status === item.status),
  );
  const waiting = appointments.filter((item) => item.status === "BOOKED" || item.status === "CHECKED_IN");
  const inProgress = appointments.filter((item) => item.status === "IN_PROGRESS");
  const stats = [
    { label: "Lịch hôm nay", value: appointments.length, icon: CalendarDays },
    { label: "Chờ khám", value: waiting.length, icon: UsersRound },
    { label: "Đang khám", value: inProgress.length, icon: Stethoscope },
    { label: "Đã hoàn thành", value: appointments.filter((item) => item.status === "COMPLETED").length, icon: Clock3 },
  ];

  const startExam = async (appointment: Appointment) => {
    try {
      const result = await start.mutateAsync(appointment.id);
      showToast("Bắt đầu khám thành công", "success");
      navigate(`/doctor/appointments/${appointment.id}`, {
        state: { appointment: { ...appointment, status: result.status, actualStartAt: result.actualStartAt } },
      });
    } catch (error) {
      showToast(getApiErrorMessage(error), "error");
    }
  };

  return (
    <div>
      <PageHeader title="Lịch khám hôm nay" description="Bắt đầu khám theo thứ tự ca trong từng buổi; giờ hiển thị là thời gian dự kiến."
        action={<Link to="/doctor/schedules"><Button variant="outline"><CalendarDays className="h-4 w-4" /> Lịch làm việc tuần</Button></Link>} />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {stats.map(({ label, value, icon: Icon }) => (
          <Card key={label} className="p-5">
            <div className="flex items-center justify-between">
              <span className="grid h-10 w-10 place-items-center rounded-xl bg-primary-50 text-primary-700"><Icon className="h-5 w-5" /></span>
              <strong className="text-2xl text-ink">{value}</strong>
            </div>
            <p className="mt-3 text-sm font-semibold text-muted">{label}</p>
          </Card>
        ))}
      </div>
      <Card className="mt-6 p-5">
        <h2 className="mb-4 text-lg font-bold text-ink">Danh sách bệnh nhân</h2>
        <div className="mb-5 flex flex-wrap gap-2" aria-label="Danh mục lịch khám">
          {categories.map((item) => (
            <Button key={item.id} variant={category === item.id ? "primary" : "outline"} aria-pressed={category === item.id} onClick={() => setCategory(item.id)}>
              {item.label} ({appointments.filter((appointment) => item.statuses.some((status) => status === appointment.status)).length})
            </Button>
          ))}
        </div>
        <AsyncStateView loading={query.isLoading} error={query.error} empty={!visibleAppointments.length}
          onRetry={() => void query.refetch()} emptyDescription="" emptyTitle={`Chưa có bệnh nhân trong mục ${activeCategory?.label ?? ""}`}>
          <div className="space-y-3">
            {visibleAppointments.map((item) => (
              <div key={item.id} className="flex flex-col justify-between gap-3 rounded-xl border border-slate-100 p-4 sm:flex-row sm:items-center">
                <Link to={`/doctor/appointments/${item.id}`} state={{ appointment: item }} className="min-w-0 flex-1 hover:text-primary-700">
                  <p className="font-semibold">{item.patientFullName} · {item.serviceName}</p>
                  <p className="mt-1 text-sm text-muted">{formatAppointmentSchedule(item)}{item.patientPhone ? ` · ${item.patientPhone}` : ""}</p>
                </Link>
                <div className="flex flex-wrap items-center gap-3">
                  <StatusBadge status={item.status} />
                  {category === "waiting" && <Button disabled={start.isPending || inProgress.length > 0 || waiting[0]?.id !== item.id} onClick={() => void startExam(item)}>
                    <Stethoscope className="h-4 w-4" /> Bắt đầu khám
                  </Button>}
                  {item.status === "IN_PROGRESS" && <Link to={`/doctor/appointments/${item.id}`} state={{ appointment: item }}><Button>Tiếp tục khám</Button></Link>}
                </div>
              </div>
            ))}
          </div>
        </AsyncStateView>
      </Card>
    </div>
  );
}
