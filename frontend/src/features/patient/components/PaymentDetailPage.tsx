import { ArrowLeft } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Card } from "@/components/ui/Card";
import { Table, Td } from "@/components/ui/Table";
import { formatDateTime, formatMoney, paymentMethodLabels } from "@/utils/format";
import { usePatientPayment } from "../hooks/usePatientData";

export default function PaymentDetailPage() {
  const { id } = useParams();
  const query = usePatientPayment(id);
  return <div><Link to="/patient/payments" className="mb-5 inline-flex items-center gap-2 text-sm font-semibold text-primary-700"><ArrowLeft className="h-4 w-4" /> Quay lại thanh toán</Link><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data || query.data.status === "DRAFT"} onRetry={() => void query.refetch()}>{query.data && query.data.status !== "DRAFT" && <><div className="mb-6 flex flex-wrap items-start justify-between gap-4"><div><h1 className="page-heading">Chi tiết thanh toán</h1><p className="page-subtitle">Mã phiếu: {query.data.id}</p></div><StatusBadge status={query.data.status} /></div><Card className="p-5 sm:p-6"><dl className="mb-6 grid gap-4 sm:grid-cols-3"><Detail label="Bác sĩ" value={query.data.doctorName} /><Detail label="Ngày khám" value={formatDateTime(query.data.scheduledStartAt)} /><Detail label="Thanh toán lúc" value={formatDateTime(query.data.paidAt)} /></dl><Table headers={["Dịch vụ", "Đơn giá", "Số lượng", "Thành tiền"]}>{query.data.items.map((item) => <tr key={item.serviceId}><Td className="font-semibold">{item.serviceName}</Td><Td>{formatMoney(item.unitPrice)}</Td><Td>{item.quantity}</Td><Td className="font-semibold">{formatMoney(item.subtotal)}</Td></tr>)}</Table><div className="mt-5 flex justify-end"><div className="min-w-64 space-y-2 text-sm"><div className="flex justify-between"><span className="text-muted">Phương thức</span><span className="font-semibold">{query.data.paymentMethod ? paymentMethodLabels[query.data.paymentMethod] : "Chưa thanh toán"}</span></div><div className="flex justify-between border-t border-slate-200 pt-3 text-lg"><span className="font-bold">Tổng cộng</span><span className="font-extrabold text-primary-700">{formatMoney(query.data.totalAmount)}</span></div></div></div></Card></>}</AsyncStateView></div>;
}

function Detail({ label, value }: { label: string; value: string }) { return <div><dt className="text-sm text-muted">{label}</dt><dd className="mt-1 font-semibold text-ink">{value}</dd></div>; }
