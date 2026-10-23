import { ArrowLeft, CalendarDays, Star, Stethoscope } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { ServiceImage } from "@/components/common/ServiceImage";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { formatMoney, formatTime, sessionLabels, weekDayLabels } from "@/utils/format";
import { useDoctor, useDoctorSchedule, useDoctorServices } from "../hooks/usePublicData";

export default function DoctorDetailPage() {
  const { id } = useParams();
  const doctor = useDoctor(id);
  const services = useDoctorServices(id);
  const schedule = useDoctorSchedule(id);
  return (
    <div className="page-container py-10 sm:py-14">
      <Link to="/doctors" className="mb-5 inline-flex items-center gap-2 text-sm font-semibold text-primary-700"><ArrowLeft className="h-4 w-4" /> Quay lại danh sách</Link>
      <AsyncStateView loading={doctor.isLoading} error={doctor.error} empty={!doctor.data} onRetry={() => void doctor.refetch()}>
        {doctor.data && <><Card className="p-6 sm:p-8"><div className="flex flex-col gap-6 sm:flex-row"><span className="grid h-20 w-20 shrink-0 place-items-center rounded-3xl bg-primary-50 text-primary-700"><Stethoscope className="h-10 w-10" /></span><div className="flex-1"><div className="flex flex-wrap items-start justify-between gap-4"><div><h1 className="text-2xl font-extrabold text-ink sm:text-3xl">{doctor.data.fullName}</h1><p className="mt-1 text-muted">{doctor.data.specialization}</p></div><StatusBadge status={doctor.data.workingStatus} /></div><div className="mt-4 flex flex-wrap gap-4 text-sm"><span className="flex items-center gap-1 text-amber-600"><Star className="h-4 w-4 fill-current" /> {doctor.data.rating.toFixed(1)}</span><span>{doctor.data.experienceYears} năm kinh nghiệm</span><span>Giấy phép: {doctor.data.licenseNumber}</span></div>{doctor.data.bio && <p className="mt-5 leading-7 text-slate-600">{doctor.data.bio}</p>}</div></div></Card>
        <div className="mt-6 grid gap-6 lg:grid-cols-2"><Card className="p-6"><h2 className="flex items-center gap-2 text-lg font-bold"><Stethoscope className="h-5 w-5 text-primary-700" /> Dịch vụ phụ trách</h2><AsyncStateView loading={services.isLoading} error={services.error} empty={!services.data?.length}>{<div className="mt-4 space-y-3">{services.data?.map((item) => <div key={item.id} className="flex items-center gap-3 rounded-xl bg-slate-50 p-3"><ServiceImage src={item.imageUrl} alt={item.name} className="h-16 w-20 shrink-0 rounded-lg" /><div className="min-w-0 flex-1"><p className="font-semibold text-ink">{item.name}</p><p className="mt-1 line-clamp-2 text-sm text-muted">{item.description || "Chưa có mô tả."}</p></div><span className="whitespace-nowrap text-sm font-bold text-primary-700">{formatMoney(item.price)}</span></div>)}</div>}</AsyncStateView></Card>
        <Card className="p-6"><h2 className="flex items-center gap-2 text-lg font-bold"><CalendarDays className="h-5 w-5 text-primary-700" /> Lịch làm việc tuần</h2><AsyncStateView loading={schedule.isLoading} error={schedule.error} empty={!schedule.data?.length}>{<div className="mt-4 space-y-2">{schedule.data?.map((item) => <div key={item.id} className="flex items-center justify-between rounded-xl border border-slate-100 px-4 py-3 text-sm"><span className="font-semibold">{weekDayLabels[item.dayOfWeek]} · {sessionLabels[item.session]}</span><span className="text-muted">{formatTime(item.startTime)}–{formatTime(item.endTime)}</span></div>)}</div>}</AsyncStateView></Card></div>
        <div className="mt-6 flex justify-end"><Link to={`/patient/book-appointment?doctorId=${doctor.data.id}`}><Button size="lg" disabled={doctor.data.workingStatus !== "ACTIVE"}><CalendarDays className="h-5 w-5" /> Đặt lịch với bác sĩ</Button></Link></div></>}
      </AsyncStateView>
    </div>
  );
}
