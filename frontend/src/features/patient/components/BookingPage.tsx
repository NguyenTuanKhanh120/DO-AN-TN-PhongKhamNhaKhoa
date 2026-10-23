import { CalendarDays, Check, ChevronRight, Clock3, Stethoscope } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { ServiceImage } from "@/components/common/ServiceImage";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { useToast } from "@/contexts/ToastContext";
import { ApiError, getApiErrorMessage } from "@/services/api/errors";
import { addCalendarDays, clinicToday } from "@/utils/date";
import { calendarStatusLabels, formatDate, formatDateTime, formatMoney } from "@/utils/format";
import { useDoctorCalendar, useServiceDoctors, useServices, useAvailableSlots } from "@/features/public/hooks/usePublicData";
import { useBookAppointment } from "../hooks/usePatientData";

export default function BookingPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [serviceId, setServiceId] = useState(searchParams.get("serviceId") ?? "");
  const [doctorId, setDoctorId] = useState(searchParams.get("doctorId") ?? "");
  const [date, setDate] = useState("");
  const [slot, setSlot] = useState("");
  const previousAppointmentId = searchParams.get("previousAppointmentId");
  const services = useServices({ active: true, page: 0, size: 100 });
  const doctors = useServiceDoctors(serviceId || undefined);
  const fromDate = clinicToday();
  const toDate = addCalendarDays(fromDate, 29);
  const calendar = useDoctorCalendar(doctorId || undefined, serviceId || undefined, fromDate, toDate);
  const slots = useAvailableSlots(doctorId || undefined, serviceId || undefined, date || undefined);
  const booking = useBookAppointment();
  const selectedService = services.data?.content.find((item) => item.id === serviceId);
  const selectedDoctor = doctors.data?.find((item) => item.id === doctorId);
  const availableDays = useMemo(() => calendar.data?.days ?? [], [calendar.data]);
  const submit = async () => {
    if (!serviceId || !doctorId || !slot) return;
    try {
      const result = await booking.mutateAsync({ serviceId, doctorId, scheduledStartAt: slot, previousAppointmentId });
      showToast("Đặt lịch khám thành công", "success");
      navigate(`/patient/appointments/${result.id}`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        showToast(error.message, "error");
        setSlot("");
        await Promise.all([slots.refetch(), calendar.refetch()]);
      } else showToast(getApiErrorMessage(error), "error");
    }
  };
  return <div><PageHeader title={previousAppointmentId ? "Đặt lịch tái khám" : "Đặt lịch khám"} description="Chọn lần lượt dịch vụ, bác sĩ, ngày và khung giờ phù hợp." /><div className="mb-6 flex flex-wrap items-center gap-2 text-xs font-semibold text-slate-500">{["Dịch vụ", "Bác sĩ", "Ngày khám", "Giờ khám", "Xác nhận"].map((label, index) => <span key={label} className="flex items-center gap-2"><span className={`grid h-7 w-7 place-items-center rounded-full ${[Boolean(serviceId), Boolean(doctorId), Boolean(date), Boolean(slot), false][index] ? "bg-primary-700 text-white" : "bg-slate-200 text-slate-600"}`}>{index + 1}</span>{label}{index < 4 && <ChevronRight className="h-4 w-4" />}</span>)}</div><div className="space-y-6"><Card className="p-5"><h2 className="mb-4 flex items-center gap-2 font-bold"><Stethoscope className="h-5 w-5 text-primary-700" /> 1. Chọn dịch vụ</h2><AsyncStateView loading={services.isLoading} error={services.error} empty={!services.data?.content.length}><div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{services.data?.content.map((item) => <button key={item.id} type="button" onClick={() => { setServiceId(item.id); setDoctorId(""); setDate(""); setSlot(""); }} className={`overflow-hidden rounded-xl border text-left transition ${serviceId === item.id ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100" : "border-slate-200 hover:border-primary-300"}`}><ServiceImage src={item.imageUrl} alt={item.name} className="h-28 w-full" /><span className="block p-4"><span className="flex items-start justify-between gap-2"><span className="font-semibold text-ink">{item.name}</span>{serviceId === item.id && <Check className="h-5 w-5 shrink-0 text-primary-700" />}</span><span className="mt-2 block text-sm font-bold text-primary-700">{formatMoney(item.price)}</span></span></button>)}</div></AsyncStateView></Card>
      {serviceId && <Card className="p-5"><h2 className="mb-4 flex items-center gap-2 font-bold"><Stethoscope className="h-5 w-5 text-primary-700" /> 2. Chọn bác sĩ</h2><AsyncStateView loading={doctors.isLoading} error={doctors.error} empty={!doctors.data?.length}><div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{doctors.data?.map((doctor) => <button key={doctor.id} type="button" disabled={doctor.workingStatus !== "ACTIVE"} onClick={() => { setDoctorId(doctor.id); setDate(""); setSlot(""); }} className={`rounded-xl border p-4 text-left transition disabled:opacity-50 ${doctorId === doctor.id ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100" : "border-slate-200 hover:border-primary-300"}`}><span className="font-semibold text-ink">{doctor.fullName}</span><span className="mt-1 block text-sm text-muted">{doctor.specialization}</span></button>)}</div></AsyncStateView></Card>}
      {doctorId && <Card className="p-5"><h2 className="mb-4 flex items-center gap-2 font-bold"><CalendarDays className="h-5 w-5 text-primary-700" /> 3. Chọn ngày khám</h2><AsyncStateView loading={calendar.isLoading} error={calendar.error} empty={!availableDays.length}><div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-7">{availableDays.map((day) => <button key={day.date} type="button" disabled={day.status !== "AVAILABLE"} onClick={() => { setDate(day.date); setSlot(""); }} className={`rounded-xl border p-3 text-center text-sm transition disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400 ${date === day.date ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100" : "border-slate-200"}`}><span className="block font-bold">{formatDate(day.date)}</span><span className="mt-1 block text-[11px]">{calendarStatusLabels[day.status]}</span></button>)}</div></AsyncStateView></Card>}
      {date && <Card className="p-5"><h2 className="mb-4 flex items-center gap-2 font-bold"><Clock3 className="h-5 w-5 text-primary-700" /> 4. Chọn khung giờ</h2><AsyncStateView loading={slots.isLoading} error={slots.error} empty={!slots.data?.length} emptyTitle="Ngày này không còn khung giờ hợp lệ"><div className="flex flex-wrap gap-3">{slots.data?.map((item) => <button key={item.startAt} type="button" onClick={() => setSlot(item.startAt)} className={`rounded-xl border px-4 py-3 text-sm font-semibold transition ${slot === item.startAt ? "border-primary-600 bg-primary-700 text-white" : "border-slate-200 bg-white hover:border-primary-300"}`}>{new Date(item.startAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit", timeZone: "Asia/Ho_Chi_Minh" })}</button>)}</div></AsyncStateView></Card>}
      {slot && <Card className="border-primary-100 bg-primary-50/40 p-5"><h2 className="font-bold text-ink">5. Xác nhận lịch khám</h2><dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-muted">Dịch vụ</dt><dd className="font-semibold">{selectedService?.name}</dd></div><div><dt className="text-muted">Bác sĩ</dt><dd className="font-semibold">{selectedDoctor?.fullName}</dd></div><div><dt className="text-muted">Thời gian</dt><dd className="font-semibold">{formatDateTime(slot)}</dd></div><div><dt className="text-muted">Loại lịch</dt><dd className="font-semibold">{previousAppointmentId ? "Tái khám" : "Khám thông thường"}</dd></div></dl><div className="mt-5 flex justify-end"><Button size="lg" onClick={() => void submit()} loading={booking.isPending}><Check className="h-5 w-5" /> Xác nhận đặt lịch</Button></div></Card>}
    </div></div>;
}
