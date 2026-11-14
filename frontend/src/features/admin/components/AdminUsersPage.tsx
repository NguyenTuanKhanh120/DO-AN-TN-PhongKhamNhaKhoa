import { zodResolver } from "@hookform/resolvers/zod";
import { Eye, Pencil, Plus, Search, Stethoscope, UserRound } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
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
import { Select } from "@/components/ui/Select";
import { Table, Td } from "@/components/ui/Table";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/contexts/ToastContext";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { getApiErrorMessage } from "@/services/api/errors";
import type { AdminUser, Role, UserStatus } from "@/types/domain";
import { formatDateTime, roleLabels, userStatusLabels } from "@/utils/format";
import {
  useAdminUser,
  useAdminUsers,
  useUpdateAdminUser,
  useUpdateUserStatus,
} from "../hooks/useAdminData";
import {
  adminUserUpdateSchema,
  type AdminUserUpdateFormValues,
} from "../utils/validation";

export default function AdminUsersPage() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [search, setSearch] = useState("");
  const [role, setRole] = useState<Role | "">("");
  const [status, setStatus] = useState<UserStatus | "">("");
  const [page, setPage] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detailTargetId, setDetailTargetId] = useState<string>();
  const [editTarget, setEditTarget] = useState<AdminUser | null>(null);
  const [statusTarget, setStatusTarget] = useState<AdminUser | null>(null);

  const query = useAdminUsers({
    search: useDebouncedValue(search),
    role: role || undefined,
    status: status || undefined,
    page,
    size: 20,
  });
  const detailQuery = useAdminUser(detailTargetId);
  const updateUser = useUpdateAdminUser();
  const updateStatus = useUpdateUserStatus();
  const form = useForm<AdminUserUpdateFormValues>({
    resolver: zodResolver(adminUserUpdateSchema),
    defaultValues: { fullName: "", email: "" },
  });

  useEffect(() => {
    if (!editTarget) return;
    form.reset({ fullName: editTarget.fullName, email: editTarget.email });
  }, [editTarget, form]);

  const saveUser = form.handleSubmit(async (values) => {
    if (!editTarget) return;
    try {
      await updateUser.mutateAsync({ id: editTarget.id, payload: values });
      showToast("Cập nhật thông tin tài khoản thành công", "success");
      setEditTarget(null);
    } catch (error) {
      form.setError("root", { message: getApiErrorMessage(error) });
    }
  });

  const confirmStatusChange = async () => {
    if (!statusTarget) return;
    const nextStatus = statusTarget.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    try {
      await updateStatus.mutateAsync({ id: statusTarget.id, status: nextStatus });
      showToast(
        nextStatus === "INACTIVE"
          ? "Vô hiệu hóa tài khoản thành công"
          : "Kích hoạt tài khoản thành công",
        "success",
      );
      setStatusTarget(null);
    } catch (error) {
      showToast(getApiErrorMessage(error), "error");
    }
  };

  return (
    <div>
      <PageHeader
        title="Quản lý tài khoản"
        description="Tìm kiếm và kiểm soát trạng thái truy cập của toàn bộ người dùng."
        action={(
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" /> Thêm tài khoản
          </Button>
        )}
      />

      <div className="filter-bar mb-6">
        <Input
          value={search}
          onChange={(event) => { setSearch(event.target.value); setPage(0); }}
          icon={<Search className="h-4 w-4" />}
          placeholder="Tìm email hoặc họ tên"
        />
        <Select
          value={role}
          onChange={(event) => { setRole(event.target.value as Role | ""); setPage(0); }}
          className="sm:w-44"
        >
          <option value="">Tất cả vai trò</option>
          {Object.entries(roleLabels).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </Select>
        <Select
          value={status}
          onChange={(event) => { setStatus(event.target.value as UserStatus | ""); setPage(0); }}
          className="sm:w-44"
        >
          <option value="">Tất cả trạng thái</option>
          <option value="ACTIVE">Hoạt động</option>
          <option value="INACTIVE">Ngừng hoạt động</option>
        </Select>
      </div>

      <Card className="p-4 sm:p-5">
        <AsyncStateView
          loading={query.isLoading}
          error={query.error}
          empty={!query.data?.content.length}
          onRetry={() => void query.refetch()}
        >
          <Table headers={["Người dùng", "Email", "Vai trò", "Trạng thái", "Thao tác"]}>
            {query.data?.content.map((item) => (
              <tr key={item.id}>
                <Td><p className="font-semibold text-ink">{item.fullName}</p></Td>
                <Td>{item.email}</Td>
                <Td>{roleLabels[item.role]}</Td>
                <Td><StatusBadge status={item.status} /></Td>
                <Td>
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="ghost" onClick={() => setDetailTargetId(item.id)}>
                      <Eye className="h-4 w-4" /> Xem chi tiết
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => setEditTarget(item)}>
                      <Pencil className="h-4 w-4" /> Sửa
                    </Button>
                    <Button
                      size="sm"
                      variant={item.status === "ACTIVE" ? "danger" : "secondary"}
                      disabled={item.id === user?.id}
                      title={item.id === user?.id ? "Không thể tự vô hiệu hóa tài khoản" : undefined}
                      onClick={() => setStatusTarget(item)}
                    >
                      {item.status === "ACTIVE" ? "Vô hiệu hóa" : "Kích hoạt"}
                    </Button>
                    {item.role === "DOCTOR" && (
                      <Link
                        to="/admin/doctors"
                        className="inline-flex min-h-9 items-center justify-center rounded-xl border border-line bg-white px-3 text-sm font-semibold text-ink transition hover:border-primary-300 hover:bg-primary-50"
                      >
                        Quản lý bác sĩ
                      </Link>
                    )}
                  </div>
                </Td>
              </tr>
            ))}
          </Table>
          {query.data && (
            <div className="mt-5">
              <Pagination
                page={query.data.page}
                totalPages={query.data.totalPages}
                totalElements={query.data.totalElements}
                onPageChange={setPage}
              />
            </div>
          )}
        </AsyncStateView>
      </Card>

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Thêm tài khoản"
        description="Chọn loại tài khoản cần tạo. Bệnh nhân tự đăng ký trên hệ thống."
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Link
            to="/admin/staff"
            state={{ openCreate: true }}
            className="rounded-xl border border-line p-4 transition hover:border-primary-300 hover:bg-primary-50"
            onClick={() => setCreateOpen(false)}
          >
            <UserRound className="mb-3 h-6 w-6 text-primary-700" />
            <p className="font-bold text-ink">Thêm nhân viên</p>
            <p className="mt-1 text-sm text-muted">Mở trang tạo tài khoản STAFF.</p>
          </Link>
          <Link
            to="/admin/doctors"
            state={{ openCreate: true }}
            className="rounded-xl border border-line p-4 transition hover:border-primary-300 hover:bg-primary-50"
            onClick={() => setCreateOpen(false)}
          >
            <Stethoscope className="mb-3 h-6 w-6 text-primary-700" />
            <p className="font-bold text-ink">Thêm bác sĩ</p>
            <p className="mt-1 text-sm text-muted">Mở trang tạo tài khoản DOCTOR.</p>
          </Link>
        </div>
      </Modal>

      <Modal
        open={Boolean(detailTargetId)}
        onClose={() => setDetailTargetId(undefined)}
        title="Chi tiết tài khoản"
      >
        <AsyncStateView
          loading={detailQuery.isLoading}
          error={detailQuery.error}
          empty={!detailQuery.data}
          onRetry={() => void detailQuery.refetch()}
        >
          {detailQuery.data && (
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <div><dt className="text-muted">Họ và tên</dt><dd className="mt-1 font-semibold">{detailQuery.data.fullName}</dd></div>
              <div><dt className="text-muted">Email</dt><dd className="mt-1 break-all font-semibold">{detailQuery.data.email}</dd></div>
              <div><dt className="text-muted">Vai trò</dt><dd className="mt-1 font-semibold">{roleLabels[detailQuery.data.role]}</dd></div>
              <div><dt className="text-muted">Trạng thái</dt><dd className="mt-1"><StatusBadge status={detailQuery.data.status} /></dd></div>
              <div><dt className="text-muted">Ngày tạo</dt><dd className="mt-1 font-semibold">{formatDateTime(detailQuery.data.createdAt)}</dd></div>
              <div><dt className="text-muted">Cập nhật gần nhất</dt><dd className="mt-1 font-semibold">{formatDateTime(detailQuery.data.updatedAt)}</dd></div>
            </dl>
          )}
        </AsyncStateView>
      </Modal>

      <Modal
        open={Boolean(editTarget)}
        onClose={() => setEditTarget(null)}
        title="Sửa thông tin tài khoản"
      >
        <form className="space-y-4" onSubmit={saveUser} noValidate>
          {form.formState.errors.root?.message && (
            <p className="rounded-xl bg-red-50 p-3 text-sm text-red-700">
              {form.formState.errors.root.message}
            </p>
          )}
          <FormField label="Họ và tên" required error={form.formState.errors.fullName?.message}>
            <Input {...form.register("fullName")} />
          </FormField>
          <FormField label="Email" required error={form.formState.errors.email?.message}>
            <Input type="email" {...form.register("email")} />
          </FormField>
          <FormField label="Vai trò">
            <Input value={editTarget ? roleLabels[editTarget.role] : ""} disabled readOnly />
          </FormField>
          <FormField label="Trạng thái">
            <Input value={editTarget ? userStatusLabels[editTarget.status] : ""} disabled readOnly />
          </FormField>
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => setEditTarget(null)}>Hủy</Button>
            <Button type="submit" loading={updateUser.isPending}>Lưu thay đổi</Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={Boolean(statusTarget)}
        onClose={() => setStatusTarget(null)}
        onConfirm={() => void confirmStatusChange()}
        title={statusTarget?.status === "ACTIVE" ? "Vô hiệu hóa tài khoản" : "Kích hoạt tài khoản"}
        description={statusTarget?.status === "ACTIVE"
          ? "Tài khoản sẽ không thể đăng nhập và tất cả phiên đăng nhập hiện tại sẽ bị thu hồi. Dữ liệu lịch sử vẫn được giữ nguyên."
          : "Tài khoản sẽ được kích hoạt lại và có thể đăng nhập theo quyền hiện có."}
        confirmText={statusTarget?.status === "ACTIVE" ? "Vô hiệu hóa" : "Kích hoạt"}
        danger={statusTarget?.status === "ACTIVE"}
        loading={updateStatus.isPending}
      />
    </div>
  );
}
