import { CalendarDays, CheckCircle2, CircleDollarSign, Clock3 } from "lucide-react";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Card } from "@/components/ui/Card";
import { clinicToday } from "@/utils/date";
import { formatDateTime, formatMoney } from "@/utils/format";
import { useStaffAppointments, useStaffPayments } from "../hooks/useStaffData";

export default function StaffDashboardPage() {
  const today = clinicToday();
  const appointments = useStaffAppointments({ fromDate: today, toDate: today, page: 0, size: 10 });
  const unpaid = useStaffPayments({ status: "UNPAID", page: 0, size: 10 });
  const paid = useStaffPayments({ status: "PAID", fromDate: today, toDate: today, page: 0, size: 1 });
  const stats = [
    { label: "Lịch hôm nay", value: appointments.data?.totalElements ?? 0, icon: CalendarDays },
    { label: "Đang khám", value: appointments.data?.content.filter((item) => item.status === "IN_PROGRESS").length ?? 0, icon: Clock3 },
    { label: "Chờ thu tiền", value: unpaid.data?.totalElements ?? 0, icon: CircleDollarSign },
    { label: "Đã thu hôm nay", value: paid.data?.totalElements ?? 0, icon: CheckCircle2 },
  ];
  return <div><PageHeader title="Tổng quan lễ tân & thu ngân" description="Theo dõi lịch toàn phòng khám và các phiếu đang chờ thu." /><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{stats.map(({ label, value, icon: Icon }) => <Card key={label} className="p-5"><div className="flex justify-between"><span className="grid h-10 w-10 place-items-center rounded-xl bg-primary-50 text-primary-700"><Icon className="h-5 w-5" /></span><strong className="text-2xl">{value}</strong></div><p className="mt-3 text-sm font-semibold text-muted">{label}</p></Card>)}</div><div className="mt-6 grid gap-6 xl:grid-cols-2"><Card className="p-5"><div className="mb-4 flex justify-between"><h2 className="font-bold">Lịch hôm nay</h2><Link to="/staff/appointments" className="text-sm font-semibold text-primary-700">Xem tất cả</Link></div><AsyncStateView loading={appointments.isLoading} error={appointments.error} empty={!appointments.data?.content.length}><div className="space-y-3">{appointments.data?.content.map((item) => <div key={item.id} className="flex justify-between gap-3 rounded-xl bg-slate-50 p-3"><div><p className="font-semibold">{item.patientFullName}</p><p className="text-xs text-muted">{formatDateTime(item.scheduledStartAt)} · {item.doctorFullName}</p></div><StatusBadge status={item.status} /></div>)}</div></AsyncStateView></Card><Card className="p-5"><div className="mb-4 flex justify-between"><h2 className="font-bold">Phiếu chờ thu</h2><Link to="/staff/payments" className="text-sm font-semibold text-primary-700">Xem tất cả</Link></div><AsyncStateView loading={unpaid.isLoading} error={unpaid.error} empty={!unpaid.data?.content.length}><div className="space-y-3">{unpaid.data?.content.map((item) => <Link key={item.id} to={`/staff/payments/${item.id}`} className="flex justify-between gap-3 rounded-xl bg-slate-50 p-3 hover:bg-primary-50"><div><p className="font-semibold">{item.patientName}</p><p className="text-xs text-muted">{item.originalServiceName}</p></div><strong className="text-primary-700">{formatMoney(item.totalAmount)}</strong></Link>)}</div></AsyncStateView></Card></div></div>;
}
