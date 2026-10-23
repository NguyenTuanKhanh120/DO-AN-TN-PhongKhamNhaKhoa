import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Search } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { FormField } from "@/components/ui/FormField";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Pagination } from "@/components/ui/Pagination";
import { PasswordInput } from "@/components/ui/PasswordInput";
import { Table, Td } from "@/components/ui/Table";
import { useToast } from "@/contexts/ToastContext";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { getApiErrorMessage } from "@/services/api/errors";
import type { AdminUser } from "@/types/domain";
import { useAdminStaff, useCreateStaff, useUpdateStaffStatus } from "../hooks/useAdminData";
import { staffSchema, type StaffFormValues } from "../utils/validation";

export default function AdminStaffPage() {
  const [search, setSearch] = useState(""); const [page, setPage] = useState(0); const [createOpen, setCreateOpen] = useState(false); const [target, setTarget] = useState<AdminUser | null>(null); const { showToast } = useToast();
  const query = useAdminStaff({ search: useDebouncedValue(search), page, size: 20 }); const create = useCreateStaff(); const status = useUpdateStaffStatus();
  const form = useForm<StaffFormValues>({ resolver: zodResolver(staffSchema), defaultValues: { fullName: "", email: "", password: "" } });
  const submit = form.handleSubmit(async (values) => { try { await create.mutateAsync(values); showToast("Tạo tài khoản nhân viên thành công", "success"); setCreateOpen(false); form.reset(); } catch (error) { form.setError("root", { message: getApiErrorMessage(error) }); } });
  const changeStatus = async () => { if (!target) return; try { await status.mutateAsync({ id: target.id, status: target.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" }); showToast("Cập nhật trạng thái nhân viên thành công", "success"); setTarget(null); } catch (error) { showToast(getApiErrorMessage(error), "error"); } };
  return <div><PageHeader title="Quản lý nhân viên" description="Tạo và quản lý tài khoản lễ tân, thu ngân." action={<Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> Thêm nhân viên</Button>} /><div className="filter-bar mb-6"><Input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} icon={<Search className="h-4 w-4" />} placeholder="Tìm nhân viên" /></div><Card className="p-4 sm:p-5"><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length}><Table headers={["Nhân viên", "Trạng thái", "Thao tác"]}>{query.data?.content.map((item) => <tr key={item.id}><Td><p className="font-semibold">{item.fullName}</p><p className="text-xs text-muted">{item.email}</p></Td><Td><StatusBadge status={item.status} /></Td><Td><Button size="sm" variant={item.status === "ACTIVE" ? "danger" : "secondary"} onClick={() => setTarget(item)}>{item.status === "ACTIVE" ? "Vô hiệu hóa" : "Kích hoạt"}</Button></Td></tr>)}</Table>{query.data && <div className="mt-5"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}</AsyncStateView></Card><Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Tạo tài khoản nhân viên"><form className="space-y-4" onSubmit={submit}>{form.formState.errors.root?.message && <p className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{form.formState.errors.root.message}</p>}<FormField label="Họ và tên" required error={form.formState.errors.fullName?.message}><Input {...form.register("fullName")} /></FormField><FormField label="Email" required error={form.formState.errors.email?.message}><Input type="email" {...form.register("email")} /></FormField><FormField label="Mật khẩu" required error={form.formState.errors.password?.message}><PasswordInput {...form.register("password")} /></FormField><div className="flex justify-end gap-3"><Button variant="outline" onClick={() => setCreateOpen(false)}>Hủy</Button><Button type="submit" loading={create.isPending}>Tạo nhân viên</Button></div></form></Modal><ConfirmDialog open={Boolean(target)} onClose={() => setTarget(null)} onConfirm={() => void changeStatus()} title="Thay đổi trạng thái nhân viên" description={`Xác nhận thay đổi trạng thái của ${target?.fullName ?? "nhân viên"}.`} danger={target?.status === "ACTIVE"} loading={status.isPending} /></div>;
}
