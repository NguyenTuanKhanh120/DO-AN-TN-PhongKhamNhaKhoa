import { CalendarDays } from "lucide-react";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { Card } from "@/components/ui/Card";
import { formatTime, sessionLabels, weekDayLabels } from "@/utils/format";
import { useDoctorSchedules } from "../hooks/useDoctorData";

export default function DoctorSchedulesPage() {
  const query = useDoctorSchedules();
  return <div><PageHeader title="Lịch làm việc tuần" description="Lịch do quản trị viên cấu hình. Bác sĩ chỉ có quyền xem." /><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.length} onRetry={() => void query.refetch()} emptyTitle="Chưa có lịch làm việc"><div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{query.data?.map((item) => <Card key={item.id} className="p-5"><div className="flex items-start justify-between"><span className="grid h-10 w-10 place-items-center rounded-xl bg-primary-50 text-primary-700"><CalendarDays className="h-5 w-5" /></span><span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">{sessionLabels[item.session]}</span></div><h2 className="mt-4 font-bold text-ink">{weekDayLabels[item.dayOfWeek]}</h2><p className="mt-1 text-sm text-muted">{formatTime(item.startTime)} – {formatTime(item.endTime)}</p></Card>)}</div></AsyncStateView></div>;
}
