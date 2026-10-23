import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, CheckCircle2, Plus, Save, Stethoscope, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "@/components/common/AsyncStateView";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { FormField } from "@/components/ui/FormField";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Textarea } from "@/components/ui/Textarea";
import { useToast } from "@/contexts/ToastContext";
import { useServices } from "@/features/public/hooks/usePublicData";
import { useUnsavedChangesWarning } from "@/hooks/useUnsavedChangesWarning";
import { ApiError, getApiErrorMessage } from "@/services/api/errors";
import type { Appointment } from "@/types/domain";
import { formatDateTime, formatMoney } from "@/utils/format";
import {
  useCompleteExamination,
  useDoctorPayment,
  useMedicalRecord,
  useSaveMedicalRecord,
  useStartExamination,
  useUpdatePaymentItems,
} from "../hooks/useDoctorData";
import { useCachedDoctorAppointment } from "../hooks/useCachedDoctorAppointment";
import { medicalRecordSchema, paymentItemsSchema, type MedicalRecordFormValues, type PaymentItemsFormValues } from "../utils/validation";

const emptyMedicalRecord: MedicalRecordFormValues = {
  diagnosis: "",
  treatment: "",
  treatmentPlan: "",
  notes: "",
  prescription: [],
  followUpRequired: false,
  recommendedFollowUpDate: "",
  followUpNote: "",
};

export default function DoctorAppointmentDetailPage() {
  const { id } = useParams();
  const cachedAppointment = useCachedDoctorAppointment(id);
  const [appointment, setAppointment] = useState<Appointment | undefined>(cachedAppointment);
  const [completeOpen, setCompleteOpen] = useState(false);
  const { showToast } = useToast();
  useEffect(() => { if (cachedAppointment) setAppointment(cachedAppointment); }, [cachedAppointment]);

  const canReadClinicalData = appointment?.status === "IN_PROGRESS" || appointment?.status === "COMPLETED";
  const medicalRecord = useMedicalRecord(id, canReadClinicalData);
  const payment = useDoctorPayment(id, Boolean(appointment));
  const services = useServices({ active: true, page: 0, size: 100 });
  const start = useStartExamination();
  const complete = useCompleteExamination();
  const recordExists = Boolean(medicalRecord.data);
  const saveRecord = useSaveMedicalRecord(id ?? "", recordExists);
  const saveItems = useUpdatePaymentItems(id ?? "");

  const recordForm = useForm<MedicalRecordFormValues>({ resolver: zodResolver(medicalRecordSchema), defaultValues: emptyMedicalRecord });
  const prescriptionFields = useFieldArray({ control: recordForm.control, name: "prescription" });
  const paymentForm = useForm<PaymentItemsFormValues>({ resolver: zodResolver(paymentItemsSchema), defaultValues: { items: [] } });
  const paymentFields = useFieldArray({ control: paymentForm.control, name: "items" });
  const followUpRequired = recordForm.watch("followUpRequired");
  useUnsavedChangesWarning(recordForm.formState.isDirty || paymentForm.formState.isDirty);

  useEffect(() => {
    if (!medicalRecord.data) return;
    recordForm.reset({
      diagnosis: medicalRecord.data.diagnosis,
      treatment: medicalRecord.data.treatment,
      treatmentPlan: medicalRecord.data.treatmentPlan ?? "",
      notes: medicalRecord.data.notes ?? "",
      prescription: medicalRecord.data.prescription,
      followUpRequired: medicalRecord.data.followUpRequired,
      recommendedFollowUpDate: medicalRecord.data.recommendedFollowUpDate ?? "",
      followUpNote: medicalRecord.data.followUpNote ?? "",
    });
  }, [medicalRecord.data, recordForm]);

  useEffect(() => {
    if (payment.data) paymentForm.reset({ items: payment.data.items.map((item) => ({ serviceId: item.serviceId, quantity: item.quantity })) });
  }, [payment.data, paymentForm]);

  if (!appointment || !id) {
    return <div><Link to="/doctor/appointments" className="mb-5 inline-flex items-center gap-2 text-sm font-semibold text-primary-700"><ArrowLeft className="h-4 w-4" /> Quay lại lịch khám</Link><EmptyState title="Không có dữ liệu ca khám trong bộ nhớ" description="Backend không cung cấp API chi tiết Appointment cho bác sĩ. Vui lòng mở ca khám từ danh sách lịch để tiếp tục." action={<Link to="/doctor/appointments"><Button>Về danh sách lịch khám</Button></Link>} /></div>;
  }

  const startExam = async () => {
    try {
      const result = await start.mutateAsync(id);
      setAppointment((current) => current ? { ...current, status: result.status, actualStartAt: result.actualStartAt } : current);
      showToast("Bắt đầu khám thành công", "success");
    } catch (error) { showToast(getApiErrorMessage(error), "error"); }
  };

  const submitRecord = recordForm.handleSubmit(async (values) => {
    try {
      await saveRecord.mutateAsync({
        ...values,
        treatmentPlan: values.treatmentPlan || null,
        notes: values.notes || null,
        recommendedFollowUpDate: values.followUpRequired ? values.recommendedFollowUpDate || null : null,
        followUpNote: values.followUpRequired ? values.followUpNote || null : null,
      });
      recordForm.reset(values);
      showToast(recordExists ? "Cập nhật bệnh án thành công" : "Lưu bệnh án thành công", "success");
    } catch (error) { showToast(getApiErrorMessage(error), "error"); }
  });

  const submitItems = paymentForm.handleSubmit(async ({ items }) => {
    try {
      await saveItems.mutateAsync(items);
      paymentForm.reset({ items });
      showToast("Cập nhật dịch vụ thực tế thành công", "success");
    } catch (error) { showToast(getApiErrorMessage(error), "error"); }
  });

  const completeExam = async () => {
    try {
      const result = await complete.mutateAsync(id);
      setAppointment((current) => current ? { ...current, status: result.status, actualEndAt: result.actualEndAt } : current);
      setCompleteOpen(false);
      showToast("Hoàn thành ca khám thành công", "success");
    } catch (error) { showToast(getApiErrorMessage(error), "error"); }
  };

  const clinicalError = medicalRecord.error instanceof ApiError && medicalRecord.error.status !== 404 ? medicalRecord.error.message : null;
  const editable = appointment.status === "IN_PROGRESS";
  const readyToComplete = recordExists && Boolean(payment.data?.items.length);

  return <div><Link to="/doctor/appointments" className="mb-5 inline-flex items-center gap-2 text-sm font-semibold text-primary-700"><ArrowLeft className="h-4 w-4" /> Quay lại lịch khám</Link><div className="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-start"><div><h1 className="page-heading">Ca khám của {appointment.patientFullName}</h1><p className="page-subtitle">{formatDateTime(appointment.scheduledStartAt)} · {appointment.serviceName}</p></div><StatusBadge status={appointment.status} /></div>
    <Card className="p-5 sm:p-6"><div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><Detail label="Bệnh nhân" value={appointment.patientFullName} /><Detail label="Số điện thoại" value={appointment.patientPhone || "Chưa cập nhật"} /><Detail label="Dịch vụ đăng ký" value={appointment.serviceName} /><Detail label="Thời gian" value={formatDateTime(appointment.scheduledStartAt)} /></div>{appointment.status === "BOOKED" && <div className="mt-5 flex justify-end border-t border-slate-100 pt-5"><Button onClick={() => void startExam()} loading={start.isPending}><Stethoscope className="h-4 w-4" /> Bắt đầu khám</Button></div>}</Card>
    {canReadClinicalData && <div className="mt-6 grid gap-6 xl:grid-cols-[1.3fr_1fr]">
      <Card className="p-5 sm:p-6"><div className="mb-5"><h2 className="text-lg font-bold text-ink">Bệnh án chính thức</h2><p className="mt-1 text-sm text-muted">Không có bản nháp và không tự động lưu. Chỉ dữ liệu đã được Backend xác nhận mới được xem là đã lưu.</p></div>{clinicalError && <p className="mb-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">{clinicalError}</p>}<form className="space-y-4" onSubmit={submitRecord}><FormField label="Chẩn đoán" required error={recordForm.formState.errors.diagnosis?.message}><Textarea disabled={!editable} {...recordForm.register("diagnosis")} /></FormField><FormField label="Nội dung điều trị" required error={recordForm.formState.errors.treatment?.message}><Textarea disabled={!editable} {...recordForm.register("treatment")} /></FormField><FormField label="Kế hoạch điều trị"><Textarea disabled={!editable} {...recordForm.register("treatmentPlan")} /></FormField><FormField label="Ghi chú"><Textarea disabled={!editable} {...recordForm.register("notes")} /></FormField><div className="rounded-xl border border-slate-200 p-4"><div className="mb-3 flex items-center justify-between"><h3 className="font-semibold text-ink">Đơn thuốc</h3>{editable && <Button size="sm" variant="outline" onClick={() => prescriptionFields.append({ medicationName: "", dosage: "", quantity: 1, instructions: "" })}><Plus className="h-4 w-4" /> Thêm thuốc</Button>}</div><div className="space-y-3">{prescriptionFields.fields.map((field, index) => <div key={field.id} className="grid gap-3 rounded-xl bg-slate-50 p-3 sm:grid-cols-2"><FormField label="Tên thuốc" error={recordForm.formState.errors.prescription?.[index]?.medicationName?.message}><Input disabled={!editable} {...recordForm.register(`prescription.${index}.medicationName`)} /></FormField><FormField label="Liều dùng" error={recordForm.formState.errors.prescription?.[index]?.dosage?.message}><Input disabled={!editable} {...recordForm.register(`prescription.${index}.dosage`)} /></FormField><FormField label="Số lượng" error={recordForm.formState.errors.prescription?.[index]?.quantity?.message}><Input type="number" min="1" disabled={!editable} {...recordForm.register(`prescription.${index}.quantity`)} /></FormField><FormField label="Hướng dẫn" error={recordForm.formState.errors.prescription?.[index]?.instructions?.message}><Input disabled={!editable} {...recordForm.register(`prescription.${index}.instructions`)} /></FormField>{editable && <Button size="sm" variant="ghost" className="sm:col-span-2 justify-self-end text-red-600" onClick={() => prescriptionFields.remove(index)}><Trash2 className="h-4 w-4" /> Xóa thuốc</Button>}</div>)}{!prescriptionFields.fields.length && <p className="text-sm text-muted">Không kê thuốc.</p>}</div></div><label className="flex items-center gap-3 rounded-xl border border-slate-200 p-4 text-sm font-semibold"><input type="checkbox" disabled={!editable} className="h-4 w-4 accent-primary-700" {...recordForm.register("followUpRequired")} /> Chỉ định bệnh nhân tái khám</label>{followUpRequired && <div className="grid gap-4 sm:grid-cols-2"><FormField label="Ngày tái khám" required error={recordForm.formState.errors.recommendedFollowUpDate?.message}><Input type="date" disabled={!editable} {...recordForm.register("recommendedFollowUpDate")} /></FormField><FormField label="Lời dặn tái khám"><Input disabled={!editable} {...recordForm.register("followUpNote")} /></FormField></div>}{editable && <div className="flex justify-end"><Button type="submit" loading={saveRecord.isPending}><Save className="h-4 w-4" /> Lưu bệnh án</Button></div>}</form></Card>
      <div className="space-y-6"><Card className="p-5 sm:p-6"><h2 className="text-lg font-bold text-ink">Dịch vụ thực tế</h2><p className="mt-1 text-sm text-muted">Không gửi đơn giá; Backend lấy giá hiện hành và tính tổng.</p>{payment.error && <p className="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">{getApiErrorMessage(payment.error)}</p>}<form className="mt-5 space-y-3" onSubmit={submitItems}>{paymentFields.fields.map((field, index) => <div key={field.id} className="grid grid-cols-[1fr_90px_auto] gap-2"><Select disabled={!editable} {...paymentForm.register(`items.${index}.serviceId`)}><option value="">Chọn dịch vụ</option>{services.data?.content.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</Select><Input type="number" min="1" max="100" disabled={!editable} {...paymentForm.register(`items.${index}.quantity`)} />{editable && <Button variant="ghost" onClick={() => paymentFields.remove(index)} aria-label="Xóa dịch vụ"><Trash2 className="h-4 w-4 text-red-600" /></Button>}</div>)}{editable && <Button size="sm" variant="outline" onClick={() => paymentFields.append({ serviceId: "", quantity: 1 })}><Plus className="h-4 w-4" /> Thêm dịch vụ</Button>}{payment.data && <div className="rounded-xl bg-slate-50 p-4"><div className="flex justify-between text-sm"><span className="text-muted">Tổng tạm tính</span><strong className="text-primary-700">{formatMoney(payment.data.totalAmount)}</strong></div></div>}{editable && <Button type="submit" className="w-full" loading={saveItems.isPending} disabled={!paymentFields.fields.length}>Lưu dịch vụ thực tế</Button>}</form></Card>{editable && <Card className="border-primary-100 bg-primary-50/40 p-5"><h2 className="font-bold text-ink">Hoàn thành ca khám</h2><ul className="mt-3 space-y-2 text-sm"><li className={recordExists ? "text-emerald-700" : "text-slate-500"}>• Đã lưu bệnh án hợp lệ</li><li className={payment.data?.items.length ? "text-emerald-700" : "text-slate-500"}>• Có ít nhất một dịch vụ thực tế</li></ul><Button className="mt-4 w-full" disabled={!readyToComplete} onClick={() => setCompleteOpen(true)}><CheckCircle2 className="h-4 w-4" /> Hoàn thành khám</Button></Card>}</div>
    </div>}
    <ConfirmDialog open={completeOpen} onClose={() => setCompleteOpen(false)} onConfirm={() => void completeExam()} title="Hoàn thành ca khám" description="Sau khi hoàn thành, bệnh án không thể chỉnh sửa và phiếu thanh toán chuyển sang Chưa thanh toán." confirmText="Hoàn thành khám" loading={complete.isPending} />
  </div>;
}

function Detail({ label, value }: { label: string; value: string }) { return <div><dt className="text-sm text-muted">{label}</dt><dd className="mt-1 font-semibold text-ink">{value}</dd></div>; }
