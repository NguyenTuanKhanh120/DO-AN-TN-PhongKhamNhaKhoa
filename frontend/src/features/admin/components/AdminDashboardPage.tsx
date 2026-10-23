import { CalendarDays, CircleDollarSign, Stethoscope, UserRound, XCircle } from "lucide-react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { Card } from "@/components/ui/Card";
import { formatMoney } from "@/utils/format";
import { useDashboardSummary, useRevenue } from "../hooks/useAdminData";

function currentMonth(): string { return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit" }).format(new Date()).slice(0, 7); }

export default function AdminDashboardPage() {
  const summary = useDashboardSummary();
  const revenue = useRevenue({ month: currentMonth() });
  const cards = summary.data ? [
    { label: "Tổng bệnh nhân", value: summary.data.totalPatients, icon: UserRound },
    { label: "Tổng bác sĩ", value: summary.data.totalDoctors, icon: Stethoscope },
    { label: "Lịch hôm nay", value: summary.data.todayAppointments, icon: CalendarDays },
    { label: "Hoàn thành hôm nay", value: summary.data.completedToday, icon: CircleDollarSign },
    { label: "Hủy hôm nay", value: summary.data.cancelledToday, icon: XCircle },
    { label: "Không đến hôm nay", value: summary.data.noShowToday, icon: XCircle },
  ] : [];
  const chartData = revenue.data ? [
    { name: "Hôm nay", amount: revenue.data.todayRevenue },
    { name: "Khoảng chọn", amount: revenue.data.rangeRevenue },
    { name: "Trong tháng", amount: revenue.data.monthlyRevenue },
  ] : [];
  return <div><PageHeader title="Tổng quan phòng khám" description="Số liệu lấy trực tiếp từ Backend và doanh thu chỉ tính phiếu đã thanh toán." /><AsyncStateView loading={summary.isLoading} error={summary.error} empty={!summary.data} onRetry={() => void summary.refetch()}><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{cards.map(({ label, value, icon: Icon }) => <Card key={label} className="p-5"><div className="flex justify-between"><span className="grid h-10 w-10 place-items-center rounded-xl bg-primary-50 text-primary-700"><Icon className="h-5 w-5" /></span><strong className="text-2xl text-ink">{value}</strong></div><p className="mt-3 text-sm font-semibold text-muted">{label}</p></Card>)}</div></AsyncStateView><Card className="mt-6 p-5 sm:p-6"><h2 className="text-lg font-bold text-ink">Doanh thu</h2><p className="mt-1 text-sm text-muted">Tháng {currentMonth()} · theo thời điểm paidAt</p><AsyncStateView loading={revenue.isLoading} error={revenue.error} empty={!revenue.data} onRetry={() => void revenue.refetch()}>{revenue.data && <><div className="mt-5 h-72"><ResponsiveContainer width="100%" height="100%"><BarChart data={chartData}><CartesianGrid strokeDasharray="3 3" vertical={false} /><XAxis dataKey="name" tick={{ fontSize: 12 }} /><YAxis tickFormatter={(value: number) => `${Math.round(value / 1_000_000)}tr`} tick={{ fontSize: 12 }} /><Tooltip formatter={(value) => formatMoney(typeof value === "number" ? value : Number(value ?? 0))} /><Bar dataKey="amount" fill="#0f766e" radius={[8, 8, 0, 0]} /></BarChart></ResponsiveContainer></div><div className="mt-5 grid gap-3 sm:grid-cols-3"><Metric label="Đã thanh toán" value={`${revenue.data.totalPaidPayments} phiếu`} /><Metric label="Chưa thanh toán" value={`${revenue.data.totalUnpaidPayments} phiếu`} /><Metric label="Tổng chưa thu" value={formatMoney(revenue.data.totalUnpaidAmount)} /></div></>}</AsyncStateView></Card></div>;
}

function Metric({ label, value }: { label: string; value: string }) { return <div className="rounded-xl bg-slate-50 p-4"><p className="text-sm text-muted">{label}</p><p className="mt-1 font-bold text-ink">{value}</p></div>; }
