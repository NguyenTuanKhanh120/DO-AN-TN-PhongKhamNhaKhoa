import { CalendarDays, CircleDollarSign, Stethoscope, UserRound, XCircle } from "lucide-react";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { Card } from "@/components/ui/Card";
import { useDashboardSummary } from "../hooks/useAdminData";
import { AdminRevenueSummary } from "./AdminRevenueSummary";

export default function AdminDashboardPage() {
  const summary = useDashboardSummary();
  const cards = summary.data ? [
    { label: "Bệnh nhân đã đến hôm nay", value: summary.data.patientsVisitedToday, icon: UserRound },
    { label: "Tổng bác sĩ", value: summary.data.totalDoctors, icon: Stethoscope },
    { label: "Lịch hôm nay", value: summary.data.todayAppointments, icon: CalendarDays },
    { label: "Hoàn thành hôm nay", value: summary.data.completedToday, icon: CircleDollarSign },
    { label: "Hủy hôm nay", value: summary.data.cancelledToday, icon: XCircle },
    { label: "Không đến hôm nay", value: summary.data.noShowToday, icon: XCircle },
  ] : [];
  return <div><PageHeader title="Tổng quan phòng khám" /><AsyncStateView loading={summary.isLoading} error={summary.error} empty={!summary.data} onRetry={() => void summary.refetch()}><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{cards.map(({ label, value, icon: Icon }) => <Card key={label} className="p-5"><div className="flex justify-between"><span className="grid h-10 w-10 place-items-center rounded-xl bg-primary-50 text-primary-700"><Icon className="h-5 w-5" /></span><strong className="text-2xl text-ink">{value}</strong></div><p className="mt-3 text-sm font-semibold text-muted">{label}</p></Card>)}</div></AsyncStateView><AdminRevenueSummary /></div>;
}
