import { CalendarPlus, CircleDollarSign, ClipboardList, Stethoscope } from "lucide-react";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { useAuth } from "@/contexts/AuthContext";
import { clinicToday } from "@/utils/date";
import { formatDateTime } from "@/utils/format";
import { usePatientAppointments } from "../hooks/usePatientData";

export default function PatientDashboardPage() {
  const { user } = useAuth();
  const appointments = usePatientAppointments({ fromDate: clinicToday(), page: 0, size: 5 });
  const shortcuts = [
    { to: "/patient/book-appointment", label: "Đặt lịch khám", description: "Chọn dịch vụ, bác sĩ và khung giờ", icon: CalendarPlus },
    { to: "/patient/appointments", label: "Lịch khám của tôi", description: "Theo dõi hoặc thay đổi lịch đã đặt", icon: ClipboardList },
    { to: "/patient/medical-records", label: "Bệnh án", description: "Xem kết quả và chỉ định tái khám", icon: Stethoscope },
    { to: "/patient/payments", label: "Thanh toán", description: "Xem chi tiết chi phí điều trị", icon: CircleDollarSign },
  ];
  return <div><PageHeader title={`Xin chào, ${user?.fullName ?? "bạn"}`} description="Chúc bạn một ngày khỏe mạnh và luôn tự tin với nụ cười của mình." action={<Link to="/patient/book-appointment"><Button><CalendarPlus className="h-4 w-4" /> Đặt lịch mới</Button></Link>} /><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{shortcuts.map(({ to, label, description, icon: Icon }) => <Link key={to} to={to}><Card className="h-full p-5 transition hover:border-primary-200 hover:shadow-soft"><Icon className="h-6 w-6 text-primary-700" /><h2 className="mt-4 font-bold text-ink">{label}</h2><p className="mt-1 text-sm text-muted">{description}</p></Card></Link>)}</div><Card className="mt-6 p-5 sm:p-6"><div className="mb-4 flex items-center justify-between"><h2 className="text-lg font-bold text-ink">Lịch khám sắp tới</h2><Link to="/patient/appointments" className="text-sm font-semibold text-primary-700">Xem tất cả</Link></div><AsyncStateView loading={appointments.isLoading} error={appointments.error} empty={!appointments.data?.content.length} emptyTitle="Bạn chưa có lịch sắp tới"><div className="space-y-3">{appointments.data?.content.map((item) => <Link key={item.id} to={`/patient/appointments/${item.id}`} className="flex flex-col justify-between gap-3 rounded-xl border border-slate-100 p-4 hover:border-primary-200 sm:flex-row sm:items-center"><div><p className="font-semibold text-ink">{item.serviceName}</p><p className="mt-1 text-sm text-muted">{item.doctorFullName} · {formatDateTime(item.scheduledStartAt)}</p></div><StatusBadge status={item.status} /></Link>)}</div></AsyncStateView></Card></div>;
}
