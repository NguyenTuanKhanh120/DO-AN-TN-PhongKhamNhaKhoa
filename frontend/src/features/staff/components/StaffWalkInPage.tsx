import { zodResolver } from "@hookform/resolvers/zod";
import { CalendarPlus, Check, Plus, Search, Stethoscope, UserRound } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { FormField } from "@/components/ui/FormField";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { Textarea } from "@/components/ui/Textarea";
import { useToast } from "@/contexts/ToastContext";
import { useServiceDoctors, useServices } from "@/features/public/hooks/usePublicData";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { toApiError } from "@/services/api/errors";
import type { StaffPatient } from "@/types/domain";
import { formatDate, formatMoney, formatQueueLabel, formatTime, genderLabels, sessionLabels } from "@/utils/format";
import {
  useCreateStaffPatient,
  useCreateWalkInAppointment,
  useStaffPatients,
  useWalkInAvailability,
} from "../hooks/useStaffData";
import { walkInPatientSchema, type WalkInPatientFormValues } from "../utils/validation";
import { WalkInAvailabilityPicker, type WalkInCaseSelection } from "./WalkInAvailabilityPicker";

const patientFormDefaults: WalkInPatientFormValues = {
  fullName: "",
  phone: "",
  dateOfBirth: "",
  gender: "UNSPECIFIED",
  address: "",
  medicalHistory: "",
  allergies: "",
};

const optionalText = (value: string): string | undefined => value.trim() || undefined;

export default function StaffWalkInPage() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [search, setSearch] = useState("");
  const [selectedPatient, setSelectedPatient] = useState<StaffPatient | null>(null);
  const [createPatientOpen, setCreatePatientOpen] = useState(false);
  const [serviceId, setServiceId] = useState("");
  const [doctorId, setDoctorId] = useState("");
  const [selectedCase, setSelectedCase] = useState<WalkInCaseSelection | null>(null);
  const debouncedSearch = useDebouncedValue(search.trim());
  const canSearch = debouncedSearch.length >= 2;

  const patients = useStaffPatients({ search: debouncedSearch, page: 0, size: 20 }, canSearch && !selectedPatient);
  const services = useServices({ active: true, page: 0, size: 100 });
  const doctors = useServiceDoctors(serviceId || undefined);
  const availability = useWalkInAvailability(doctorId || undefined, serviceId || undefined);
  const createPatient = useCreateStaffPatient();
  const createAppointment = useCreateWalkInAppointment();
  const selectedService = services.data?.content.find((service) => service.id === serviceId);
  const selectedDoctor = doctors.data?.find((doctor) => doctor.id === doctorId);

  const patientForm = useForm<WalkInPatientFormValues>({
    resolver: zodResolver(walkInPatientSchema),
    defaultValues: patientFormDefaults,
  });

  const openCreatePatient = () => {
    const query = search.trim();
    const looksLikePhone = /^[+\d\s().-]+$/.test(query);
    patientForm.reset({
      ...patientFormDefaults,
      fullName: looksLikePhone ? "" : query,
      phone: looksLikePhone ? query : "",
    });
    setCreatePatientOpen(true);
  };

  const submitPatient = patientForm.handleSubmit(async (values) => {
    try {
      const patient = await createPatient.mutateAsync({
        fullName: values.fullName.trim(),
        phone: values.phone.trim(),
        dateOfBirth: optionalText(values.dateOfBirth),
        gender: values.gender,
        address: optionalText(values.address),
        medicalHistory: optionalText(values.medicalHistory),
        allergies: optionalText(values.allergies),
      });
      setSelectedPatient(patient);
      setCreatePatientOpen(false);
      setSearch("");
      showToast("Tạo và chọn hồ sơ bệnh nhân thành công", "success");
    } catch (error) {
      const apiError = toApiError(error);
      for (const field of apiError.fields) {
        if (field.field === "fullName") patientForm.setError("fullName", { message: field.message });
        if (field.field === "phone") patientForm.setError("phone", { message: field.message });
      }
      if (!apiError.fields.length) patientForm.setError("root", { message: apiError.message });
    }
  });

  const submitAppointment = async () => {
    if (!selectedPatient || !serviceId || !doctorId || !selectedCase) return;
    try {
      await createAppointment.mutateAsync({
        patientId: selectedPatient.id,
        doctorId,
        serviceId,
        session: selectedCase.session,
        queueNumber: selectedCase.queueNumber,
      });
      showToast("Tạo lịch WALK_IN thành công", "success");
      navigate("/staff/appointments");
    } catch (error) {
      const apiError = toApiError(error);
      showToast(apiError.message, "error");
      if (apiError.status === 409) {
        setSelectedCase(null);
        await availability.refetch();
      }
    }
  };

  const selectService = (id: string) => {
    setServiceId(id);
    setDoctorId("");
    setSelectedCase(null);
  };

  const selectDoctor = (id: string) => {
    setDoctorId(id);
    setSelectedCase(null);
  };

  const progress = [
    Boolean(selectedPatient),
    Boolean(serviceId),
    Boolean(doctorId),
    Boolean(selectedCase),
  ];

  return (
    <div>
      <PageHeader
        title="Tiếp nhận bệnh nhân trực tiếp"
        description="Tìm hoặc tạo hồ sơ bệnh nhân, sau đó chọn ca còn trống trong ngày hôm nay."
      />
      <div className="mb-6 flex flex-wrap items-center gap-2 text-xs font-semibold text-slate-500">
        {["Bệnh nhân", "Dịch vụ", "Bác sĩ", "Ca khám"].map((label, index) => (
          <span key={label} className="flex items-center gap-2">
            <span className={`grid h-7 w-7 place-items-center rounded-full ${progress[index] ? "bg-primary-700 text-white" : "bg-slate-200 text-slate-600"}`}>
              {index + 1}
            </span>
            {label}
          </span>
        ))}
      </div>

      <div className="space-y-6">
        <Card className="p-5">
          <h2 className="mb-4 flex items-center gap-2 font-bold"><UserRound className="h-5 w-5 text-primary-700" /> 1. Chọn bệnh nhân</h2>
          {selectedPatient ? (
            <div className="flex flex-col justify-between gap-4 rounded-xl border border-primary-200 bg-primary-50 p-4 sm:flex-row sm:items-center">
              <div>
                <p className="font-bold text-ink">{selectedPatient.fullName}</p>
                <p className="mt-1 text-sm text-muted">{selectedPatient.phone}{selectedPatient.dateOfBirth ? ` · Sinh ngày ${formatDate(selectedPatient.dateOfBirth)}` : ""}</p>
              </div>
              <Button variant="outline" onClick={() => setSelectedPatient(null)}>Đổi bệnh nhân</Button>
            </div>
          ) : (
            <div className="space-y-4">
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                icon={<Search className="h-4 w-4" />}
                placeholder="Tìm theo họ tên, số điện thoại hoặc mã bệnh nhân"
                aria-label="Tìm bệnh nhân"
              />
              {!canSearch ? (
                <p className="rounded-xl bg-slate-50 p-4 text-sm text-muted">Nhập ít nhất 2 ký tự để kiểm tra hồ sơ bệnh nhân trước khi tạo mới.</p>
              ) : (
                <AsyncStateView
                  loading={patients.isLoading}
                  error={patients.error}
                  empty={!patients.data?.content.length}
                  onRetry={() => void patients.refetch()}
                  emptyTitle="Không tìm thấy bệnh nhân"
                  emptyDescription="Hãy kiểm tra lại từ khóa hoặc tạo hồ sơ bệnh nhân tại quầy."
                >
                  <div className="grid gap-3 sm:grid-cols-2">
                    {patients.data?.content.map((patient) => (
                      <button
                        key={patient.id}
                        type="button"
                        onClick={() => setSelectedPatient(patient)}
                        className="rounded-xl border border-slate-200 p-4 text-left transition hover:border-primary-300 hover:bg-primary-50"
                      >
                        <span className="block font-semibold text-ink">{patient.fullName}</span>
                        <span className="mt-1 block text-sm text-muted">{patient.phone}</span>
                      </button>
                    ))}
                  </div>
                </AsyncStateView>
              )}
              {canSearch && patients.isSuccess && (
                <div className="flex justify-end">
                  <Button variant="outline" onClick={openCreatePatient}><Plus className="h-4 w-4" /> Tạo hồ sơ bệnh nhân mới</Button>
                </div>
              )}
            </div>
          )}
        </Card>

        {selectedPatient && (
          <Card className="p-5">
            <h2 className="mb-4 flex items-center gap-2 font-bold"><Stethoscope className="h-5 w-5 text-primary-700" /> 2. Chọn dịch vụ</h2>
            <AsyncStateView loading={services.isLoading} error={services.error} empty={!services.data?.content.length} onRetry={() => void services.refetch()}>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {services.data?.content.map((service) => (
                  <button
                    key={service.id}
                    type="button"
                    onClick={() => selectService(service.id)}
                    className={`rounded-xl border p-4 text-left transition ${serviceId === service.id ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100" : "border-slate-200 hover:border-primary-300"}`}
                  >
                    <span className="flex items-start justify-between gap-2"><span className="font-semibold text-ink">{service.name}</span>{serviceId === service.id && <Check className="h-5 w-5 text-primary-700" />}</span>
                    <span className="mt-2 block text-sm font-bold text-primary-700">{formatMoney(service.price)}</span>
                  </button>
                ))}
              </div>
            </AsyncStateView>
          </Card>
        )}

        {serviceId && (
          <Card className="p-5">
            <h2 className="mb-4 flex items-center gap-2 font-bold"><Stethoscope className="h-5 w-5 text-primary-700" /> 3. Chọn bác sĩ</h2>
            <AsyncStateView loading={doctors.isLoading} error={doctors.error} empty={!doctors.data?.length} onRetry={() => void doctors.refetch()}>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {doctors.data?.map((doctor) => (
                  <button
                    key={doctor.id}
                    type="button"
                    disabled={doctor.workingStatus !== "ACTIVE"}
                    onClick={() => selectDoctor(doctor.id)}
                    className={`rounded-xl border p-4 text-left transition disabled:cursor-not-allowed disabled:opacity-50 ${doctorId === doctor.id ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100" : "border-slate-200 hover:border-primary-300"}`}
                  >
                    <span className="font-semibold text-ink">{doctor.fullName}</span>
                    <span className="mt-1 block text-sm text-muted">{doctor.specialization}</span>
                  </button>
                ))}
              </div>
            </AsyncStateView>
          </Card>
        )}

        {doctorId && (
          <Card className="p-5">
            <h2 className="mb-4 flex items-center gap-2 font-bold"><CalendarPlus className="h-5 w-5 text-primary-700" /> 4. Chọn ca còn trống hôm nay</h2>
            <AsyncStateView
              loading={availability.isLoading}
              error={availability.error}
              empty={!availability.data?.sessions.length}
              onRetry={() => void availability.refetch()}
              emptyTitle="Không có ca khám hôm nay"
            >
              {availability.data && (
                <WalkInAvailabilityPicker availability={availability.data} selected={selectedCase} onSelect={setSelectedCase} />
              )}
            </AsyncStateView>
          </Card>
        )}

        {selectedPatient && selectedService && selectedDoctor && selectedCase && availability.data && (
          <Card className="border-primary-100 bg-primary-50/40 p-5">
            <h2 className="font-bold text-ink">Xác nhận tiếp nhận WALK_IN</h2>
            <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
              <div><dt className="text-muted">Bệnh nhân</dt><dd className="font-semibold">{selectedPatient.fullName}</dd></div>
              <div><dt className="text-muted">Dịch vụ</dt><dd className="font-semibold">{selectedService.name}</dd></div>
              <div><dt className="text-muted">Bác sĩ</dt><dd className="font-semibold">{selectedDoctor.fullName}</dd></div>
              <div><dt className="text-muted">Ngày khám</dt><dd className="font-semibold">{formatDate(availability.data.date)}</dd></div>
              <div><dt className="text-muted">Buổi khám</dt><dd className="font-semibold">{sessionLabels[selectedCase.session]}</dd></div>
              <div><dt className="text-muted">Ca khám</dt><dd className="font-semibold">{formatQueueLabel(selectedCase.queueNumber)} · dự kiến {formatTime(selectedCase.estimatedStartTime)}</dd></div>
            </dl>
            <div className="mt-5 flex justify-end">
              <Button size="lg" loading={createAppointment.isPending} onClick={() => void submitAppointment()}>
                <CalendarPlus className="h-5 w-5" /> Xác nhận tạo lịch WALK_IN
              </Button>
            </div>
          </Card>
        )}
      </div>

      <Modal
        open={createPatientOpen}
        onClose={() => setCreatePatientOpen(false)}
        title="Tạo hồ sơ bệnh nhân tại quầy"
        description="Không tạo tài khoản đăng nhập hoặc yêu cầu email, mật khẩu."
      >
        <form className="space-y-4" onSubmit={submitPatient} noValidate>
          {patientForm.formState.errors.root?.message && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{patientForm.formState.errors.root.message}</p>}
          <FormField label="Họ và tên" required error={patientForm.formState.errors.fullName?.message}>
            <Input error={Boolean(patientForm.formState.errors.fullName)} {...patientForm.register("fullName")} />
          </FormField>
          <FormField label="Số điện thoại" required error={patientForm.formState.errors.phone?.message}>
            <Input error={Boolean(patientForm.formState.errors.phone)} {...patientForm.register("phone")} />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="Ngày sinh" error={patientForm.formState.errors.dateOfBirth?.message}>
              <Input type="date" {...patientForm.register("dateOfBirth")} />
            </FormField>
            <FormField label="Giới tính" error={patientForm.formState.errors.gender?.message}>
              <Select {...patientForm.register("gender")}>
                {Object.entries(genderLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </Select>
            </FormField>
          </div>
          <FormField label="Địa chỉ"><Input {...patientForm.register("address")} /></FormField>
          <FormField label="Tiền sử bệnh"><Textarea {...patientForm.register("medicalHistory")} /></FormField>
          <FormField label="Dị ứng"><Textarea {...patientForm.register("allergies")} /></FormField>
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => setCreatePatientOpen(false)}>Hủy</Button>
            <Button type="submit" loading={createPatient.isPending}>Lưu hồ sơ bệnh nhân</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
