import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { FormField } from "@/components/ui/FormField";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Textarea } from "@/components/ui/Textarea";
import { useToast } from "@/contexts/ToastContext";
import { toApiError } from "@/services/api/errors";
import { genderLabels } from "@/utils/format";
import { usePatientProfile, useUpdateProfile } from "../hooks/usePatientData";
import { profileSchema, type ProfileFormValues } from "../utils/validation";

export default function PatientProfilePage() {
  const profile = usePatientProfile();
  const update = useUpdateProfile();
  const { showToast } = useToast();
  const { register, reset, handleSubmit, setError, formState: { errors } } = useForm<ProfileFormValues>({ resolver: zodResolver(profileSchema), defaultValues: { fullName: "", dateOfBirth: "", gender: "UNSPECIFIED", phone: "", address: "", medicalHistory: "", allergies: "" } });
  useEffect(() => {
    if (profile.data) reset({ fullName: profile.data.fullName, dateOfBirth: profile.data.dateOfBirth ?? "", gender: profile.data.gender, phone: profile.data.phone ?? "", address: profile.data.address ?? "", medicalHistory: profile.data.medicalHistory ?? "", allergies: profile.data.allergies ?? "" });
  }, [profile.data, reset]);
  const submit = handleSubmit(async (values) => {
    try {
      await update.mutateAsync({ ...values, dateOfBirth: values.dateOfBirth || null, phone: values.phone || null, address: values.address || null, medicalHistory: values.medicalHistory || null, allergies: values.allergies || null });
      showToast("Cập nhật hồ sơ thành công", "success");
    } catch (error) {
      const apiError = toApiError(error);
      apiError.fields.forEach((field) => {
        if (field.field in values) setError(field.field as keyof ProfileFormValues, { message: field.message });
      });
      if (!apiError.fields.length) showToast(apiError.message, "error");
    }
  });
  return <div><PageHeader title="Hồ sơ cá nhân" description="Thông tin này giúp bác sĩ tư vấn và chăm sóc bạn phù hợp hơn." /><AsyncStateView loading={profile.isLoading} error={profile.error} empty={!profile.data} onRetry={() => void profile.refetch()}><Card className="max-w-3xl p-5 sm:p-7"><form className="grid gap-5 sm:grid-cols-2" onSubmit={submit}><FormField label="Họ và tên" required error={errors.fullName?.message}><Input {...register("fullName")} error={Boolean(errors.fullName)} /></FormField><FormField label="Email"><Input value={profile.data?.email ?? ""} disabled /></FormField><FormField label="Ngày sinh"><Input type="date" {...register("dateOfBirth")} /></FormField><FormField label="Giới tính" required error={errors.gender?.message}><Select {...register("gender")}>{Object.entries(genderLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select></FormField><FormField label="Số điện thoại" error={errors.phone?.message}><Input type="tel" {...register("phone")} /></FormField><FormField label="Địa chỉ"><Input {...register("address")} /></FormField><div className="sm:col-span-2"><FormField label="Tiền sử bệnh"><Textarea {...register("medicalHistory")} placeholder="Thông tin bệnh lý cần lưu ý" /></FormField></div><div className="sm:col-span-2"><FormField label="Dị ứng"><Textarea {...register("allergies")} placeholder="Thuốc hoặc thành phần từng gây dị ứng" /></FormField></div><div className="sm:col-span-2 flex justify-end"><Button type="submit" loading={update.isPending}>Lưu thay đổi</Button></div></form></Card></AsyncStateView></div>;
}
