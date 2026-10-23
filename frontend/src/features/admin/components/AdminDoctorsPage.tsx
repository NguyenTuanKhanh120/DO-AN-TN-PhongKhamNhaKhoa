import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Search } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { FormField } from "@/components/ui/FormField";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Pagination } from "@/components/ui/Pagination";
import { PasswordInput } from "@/components/ui/PasswordInput";
import { Textarea } from "@/components/ui/Textarea";
import { useToast } from "@/contexts/ToastContext";
import { useDoctors } from "@/features/public/hooks/usePublicData";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { getApiErrorMessage } from "@/services/api/errors";
import { useCreateDoctor } from "../hooks/useAdminData";
import { doctorCreateSchema, type DoctorCreateFormValues } from "../utils/validation";

export default function AdminDoctorsPage() {
  const [search, setSearch] = useState(""); const [page, setPage] = useState(0); const [open, setOpen] = useState(false); const { showToast } = useToast();
  const query = useDoctors({ search: useDebouncedValue(search), page, size: 20 }); const create = useCreateDoctor();
  const form = useForm<DoctorCreateFormValues>({ resolver: zodResolver(doctorCreateSchema), defaultValues: { fullName: "", email: "", password: "", specialization: "", licenseNumber: "", bio: "", experienceYears: 0 } });
  const submit = form.handleSubmit(async (values) => { try { await create.mutateAsync({ ...values, bio: values.bio || null }); showToast("Tạo bác sĩ và 12 lịch mặc định thành công", "success"); setOpen(false); form.reset(); } catch (error) { form.setError("root", { message: getApiErrorMessage(error) }); } });
  return <div><PageHeader title="Quản lý bác sĩ" description="Khi tạo mới, Backend tự động tạo 12 lịch từ Thứ Hai đến Thứ Bảy." action={<Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" /> Thêm bác sĩ</Button>} /><div className="filter-bar mb-6"><Input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} icon={<Search className="h-4 w-4" />} placeholder="Tìm bác sĩ hoặc chuyên khoa" /></div><Card className="p-4 sm:p-5"><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length}><div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{query.data?.content.map((doctor) => <Link key={doctor.id} to={`/admin/doctors/${doctor.id}`} className="rounded-xl border border-slate-200 p-4 transition hover:border-primary-300 hover:bg-primary-50/30"><div className="flex items-start justify-between gap-3"><div><p className="font-bold text-ink">{doctor.fullName}</p><p className="mt-1 text-sm text-muted">{doctor.specialization}</p></div><StatusBadge status={doctor.workingStatus} /></div><p className="mt-3 text-sm text-slate-600">{doctor.experienceYears} năm kinh nghiệm · {doctor.rating.toFixed(1)} ★</p></Link>)}</div>{query.data && <div className="mt-5"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}</AsyncStateView></Card><Modal open={open} onClose={() => setOpen(false)} title="Tạo tài khoản bác sĩ" description="Lịch làm việc mặc định sẽ do Backend tạo tự động." className="max-w-2xl"><form className="grid gap-4 sm:grid-cols-2" onSubmit={submit}>{form.formState.errors.root?.message && <p className="rounded-xl bg-red-50 p-3 text-sm text-red-700 sm:col-span-2">{form.formState.errors.root.message}</p>}<FormField label="Họ và tên" required error={form.formState.errors.fullName?.message}><Input {...form.register("fullName")} /></FormField><FormField label="Email" required error={form.formState.errors.email?.message}><Input type="email" {...form.register("email")} /></FormField><FormField label="Mật khẩu" required error={form.formState.errors.password?.message}><PasswordInput {...form.register("password")} /></FormField><FormField label="Chuyên khoa" required error={form.formState.errors.specialization?.message}><Input {...form.register("specialization")} /></FormField><FormField label="Giấy phép hành nghề" required error={form.formState.errors.licenseNumber?.message}><Input {...form.register("licenseNumber")} /></FormField><FormField label="Số năm kinh nghiệm" required error={form.formState.errors.experienceYears?.message}><Input type="number" min="0" {...form.register("experienceYears")} /></FormField><div className="sm:col-span-2"><FormField label="Giới thiệu"><Textarea {...form.register("bio")} /></FormField></div><div className="flex justify-end gap-3 sm:col-span-2"><Button variant="outline" onClick={() => setOpen(false)}>Hủy</Button><Button type="submit" loading={create.isPending}>Tạo bác sĩ</Button></div></form></Modal></div>;
}
