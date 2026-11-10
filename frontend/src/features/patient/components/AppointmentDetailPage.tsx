import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, CalendarClock, XCircle } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useParams } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { AvailableCasePicker } from "@/components/common/AvailableCasePicker";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { FormField } from "@/components/ui/FormField";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Textarea } from "@/components/ui/Textarea";
import { useToast } from "@/contexts/ToastContext";
import { useAvailableSlots } from "@/features/public/hooks/usePublicData";
import { ApiError, getApiErrorMessage } from "@/services/api/errors";
import type { AvailableSlot } from "@/types/domain";
import { clinicToday } from "@/utils/date";
import { formatAppointmentSchedule } from "@/utils/format";
import { useCancelAppointment, usePatientAppointment, useRescheduleAppointment } from "../hooks/usePatientData";
import { reasonSchema, type ReasonFormValues } from "../utils/validation";

export default function AppointmentDetailPage() {
  const { id } = useParams();
  const query = usePatientAppointment(id);
  const cancel = useCancelAppointment();
  const reschedule = useRescheduleAppointment();
  const { showToast } = useToast();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [date, setDate] = useState("");
  const [selectedCase, setSelectedCase] = useState<AvailableSlot | null>(null);
  const slots = useAvailableSlots(query.data?.doctorId, query.data?.serviceId, date || undefined);
  const cancelForm = useForm<ReasonFormValues>({ resolver: zodResolver(reasonSchema), defaultValues: { reason: "" } });
  const rescheduleForm = useForm<ReasonFormValues>({ resolver: zodResolver(reasonSchema), defaultValues: { reason: "" } });

  const onCancel = cancelForm.handleSubmit(async ({ reason }) => {
    if (!id) return;
    try {
      await cancel.mutateAsync({ id, reason });
      setCancelOpen(false);
      showToast("Hủy lịch hẹn thành công", "success");
    } catch (error) {
      cancelForm.setError("root", { message: getApiErrorMessage(error) });
    }
  });

  const onReschedule = rescheduleForm.handleSubmit(async ({ reason }) => {
    if (!id || !selectedCase) return;
    try {
      await reschedule.mutateAsync({
        id,
        appointmentDate: selectedCase.date,
        session: selectedCase.session,
        queueNumber: selectedCase.queueNumber,
        reason,
      });
      setRescheduleOpen(false);
      showToast("Đổi lịch hẹn thành công", "success");
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setSelectedCase(null);
        await slots.refetch();
      }
      rescheduleForm.setError("root", { message: getApiErrorMessage(error) });
    }
  });

  return (
    <div>
      <Link to="/patient/appointments" className="mb-5 inline-flex items-center gap-2 text-sm font-semibold text-primary-700"><ArrowLeft className="h-4 w-4" /> Quay lại lịch khám</Link>
      <AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data} onRetry={() => void query.refetch()}>
        {query.data && <>
          <div className="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-start"><h1 className="page-heading">Chi tiết lịch khám</h1><StatusBadge status={query.data.status} /></div>
          <Card className="p-6">
            <dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              <Detail label="Lịch dự kiến" value={formatAppointmentSchedule(query.data)} />
              <Detail label="Dịch vụ" value={query.data.serviceName} />
              <Detail label="Bác sĩ" value={query.data.doctorFullName} />
              <Detail label="Chuyên khoa" value={query.data.doctorSpecialization} />
              <Detail label="Số điện thoại bệnh nhân" value={query.data.patientPhone || "Chưa cập nhật"} />
              {query.data.cancellationReason && <Detail label="Lý do hủy" value={query.data.cancellationReason} />}
            </dl>
            {query.data.status === "BOOKED" && <div className="mt-6 flex flex-wrap justify-end gap-3 border-t border-slate-100 pt-5"><Button variant="outline" onClick={() => setRescheduleOpen(true)}><CalendarClock className="h-4 w-4" /> Đổi lịch</Button><Button variant="danger" onClick={() => setCancelOpen(true)}><XCircle className="h-4 w-4" /> Hủy lịch</Button></div>}
          </Card>
        </>}
      </AsyncStateView>

      <Modal open={cancelOpen} onClose={() => setCancelOpen(false)} title="Hủy lịch khám" description="Bạn chỉ có thể tự hủy lịch của ngày khám trước 05:00 sáng cùng ngày.">
        <form className="space-y-4" onSubmit={onCancel}>
          {cancelForm.formState.errors.root?.message && <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{cancelForm.formState.errors.root.message}</p>}
          <FormField label="Lý do hủy" required error={cancelForm.formState.errors.reason?.message}><Textarea {...cancelForm.register("reason")} /></FormField>
          <div className="flex justify-end gap-3"><Button variant="outline" onClick={() => setCancelOpen(false)}>Quay lại</Button><Button type="submit" variant="danger" loading={cancel.isPending}>Xác nhận hủy</Button></div>
        </form>
      </Modal>

      <Modal open={rescheduleOpen} onClose={() => setRescheduleOpen(false)} title="Đổi lịch khám" description="Chọn buổi và ca còn trống của cùng bác sĩ, dịch vụ. Lịch trong ngày chỉ được tự đổi trước 05:00 sáng.">
        <form className="space-y-4" onSubmit={onReschedule}>
          {rescheduleForm.formState.errors.root?.message && <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{rescheduleForm.formState.errors.root.message}</p>}
          <FormField label="Ngày mới" required><Input type="date" min={clinicToday()} value={date} onChange={(event) => { setDate(event.target.value); setSelectedCase(null); }} /></FormField>
          {date && <AsyncStateView loading={slots.isLoading} error={slots.error} empty={!slots.data?.length} emptyTitle="Không còn ca khám trống"><AvailableCasePicker slots={slots.data ?? []} selected={selectedCase} onSelect={setSelectedCase} /></AsyncStateView>}
          <FormField label="Lý do đổi lịch" required error={rescheduleForm.formState.errors.reason?.message}><Textarea {...rescheduleForm.register("reason")} /></FormField>
          <div className="flex justify-end gap-3"><Button variant="outline" onClick={() => setRescheduleOpen(false)}>Quay lại</Button><Button type="submit" loading={reschedule.isPending} disabled={!selectedCase}>Xác nhận đổi lịch</Button></div>
        </form>
      </Modal>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-sm text-muted">{label}</dt><dd className="mt-1 font-semibold text-ink">{value}</dd></div>;
}
