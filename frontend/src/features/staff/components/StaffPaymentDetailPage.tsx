import { ArrowLeft } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Select } from "@/components/ui/Select";
import { Table, Td } from "@/components/ui/Table";
import { useToast } from "@/contexts/ToastContext";
import { ApiError, getApiErrorMessage } from "@/services/api/errors";
import type { PaymentMethod } from "@/types/domain";
import { formatDateTime, formatMoney, paymentMethodLabels } from "@/utils/format";
import { useConfirmStaffPayment, useStaffPayment } from "../hooks/useStaffData";

export default function StaffPaymentDetailPage() {
  const { id } = useParams(); const query = useStaffPayment(id); const confirm = useConfirmStaffPayment(id ?? ""); const { showToast } = useToast(); const [method, setMethod] = useState<PaymentMethod>("CASH"); const [open, setOpen] = useState(false);
  const submit = async () => { try { await confirm.mutateAsync(method); setOpen(false); showToast("Xác nhận thanh toán thành công", "success"); } catch (error) { if (error instanceof ApiError && error.status === 409) await query.refetch(); showToast(getApiErrorMessage(error), "error"); } };
  return <div><Link to="/staff/payments" className="mb-5 inline-flex items-center gap-2 text-sm font-semibold text-primary-700"><ArrowLeft className="h-4 w-4" /> Quay lại thanh toán</Link><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data} onRetry={() => void query.refetch()}>{query.data && <><div className="mb-6 flex justify-between gap-4"><div><h1 className="page-heading">Phiếu thanh toán</h1><p className="page-subtitle">{query.data.patientName} · {formatDateTime(query.data.scheduledStartAt)}</p></div><StatusBadge status={query.data.status} /></div><Card className="p-5 sm:p-6"><div className="mb-5 grid gap-4 sm:grid-cols-3"><Detail label="Bệnh nhân" value={query.data.patientName} /><Detail label="Bác sĩ" value={query.data.doctorName} /><Detail label="Số điện thoại" value={query.data.patientPhone || "Chưa cập nhật"} /></div><Table headers={["Dịch vụ", "Đơn giá", "Số lượng", "Thành tiền"]}>{query.data.items.map((item) => <tr key={item.serviceId}><Td className="font-semibold">{item.serviceName}</Td><Td>{formatMoney(item.unitPrice)}</Td><Td>{item.quantity}</Td><Td className="font-semibold">{formatMoney(item.subtotal)}</Td></tr>)}</Table><div className="mt-5 flex flex-col items-end gap-4"><p className="text-xl font-extrabold text-primary-700">Tổng: {formatMoney(query.data.totalAmount)}</p>{query.data.status === "UNPAID" ? <div className="flex flex-col gap-3 sm:flex-row"><Select value={method} onChange={(event) => setMethod(event.target.value as PaymentMethod)}><option value="CASH">Tiền mặt</option><option value="BANK_TRANSFER">Chuyển khoản</option></Select><Button onClick={() => setOpen(true)}>Xác nhận đã thu</Button></div> : <p className="text-sm text-muted">{query.data.paymentMethod ? paymentMethodLabels[query.data.paymentMethod] : "—"} · {formatDateTime(query.data.paidAt)}</p>}</div></Card></>}</AsyncStateView><ConfirmDialog open={open} onClose={() => setOpen(false)} onConfirm={() => void submit()} title="Xác nhận đã thu tiền" description={`Phương thức: ${paymentMethodLabels[method]}. Thao tác này không thể xác nhận lại sau khi phiếu chuyển sang Đã thanh toán.`} confirmText="Xác nhận đã thu" loading={confirm.isPending} /></div>;
}

function Detail({ label, value }: { label: string; value: string }) { return <div><dt className="text-sm text-muted">{label}</dt><dd className="mt-1 font-semibold">{value}</dd></div>; }
