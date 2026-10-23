import { Search } from "lucide-react";
import { useState } from "react";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Input } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { Select } from "@/components/ui/Select";
import { Table, Td } from "@/components/ui/Table";
import { useAuth } from "@/contexts/AuthContext";
import { useToast } from "@/contexts/ToastContext";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { getApiErrorMessage } from "@/services/api/errors";
import type { AdminUser, Role, UserStatus } from "@/types/domain";
import { roleLabels } from "@/utils/format";
import { useAdminUsers, useUpdateUserStatus } from "../hooks/useAdminData";

export default function AdminUsersPage() {
  const { user } = useAuth(); const { showToast } = useToast(); const [search, setSearch] = useState(""); const [role, setRole] = useState<Role | "">(""); const [status, setStatus] = useState<UserStatus | "">(""); const [page, setPage] = useState(0); const [target, setTarget] = useState<AdminUser | null>(null);
  const query = useAdminUsers({ search: useDebouncedValue(search), role: role || undefined, status: status || undefined, page, size: 20 }); const update = useUpdateUserStatus();
  const confirm = async () => { if (!target) return; const next = target.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"; try { await update.mutateAsync({ id: target.id, status: next }); showToast("Cập nhật trạng thái tài khoản thành công", "success"); setTarget(null); } catch (error) { showToast(getApiErrorMessage(error), "error"); } };
  return <div><PageHeader title="Quản lý tài khoản" description="Tìm kiếm và kiểm soát trạng thái truy cập của toàn bộ người dùng." /><div className="filter-bar mb-6"><Input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} icon={<Search className="h-4 w-4" />} placeholder="Tìm email hoặc họ tên" /><Select value={role} onChange={(event) => { setRole(event.target.value as Role | ""); setPage(0); }} className="sm:w-44"><option value="">Tất cả vai trò</option>{Object.entries(roleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select><Select value={status} onChange={(event) => { setStatus(event.target.value as UserStatus | ""); setPage(0); }} className="sm:w-44"><option value="">Tất cả trạng thái</option><option value="ACTIVE">Hoạt động</option><option value="INACTIVE">Ngừng hoạt động</option></Select></div><Card className="p-4 sm:p-5"><AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length} onRetry={() => void query.refetch()}><Table headers={["Người dùng", "Vai trò", "Trạng thái", "Thao tác"]}>{query.data?.content.map((item) => <tr key={item.id}><Td><p className="font-semibold text-ink">{item.fullName}</p><p className="text-xs text-muted">{item.email}</p></Td><Td>{roleLabels[item.role]}</Td><Td><StatusBadge status={item.status} /></Td><Td><Button size="sm" variant={item.status === "ACTIVE" ? "danger" : "secondary"} disabled={item.id === user?.id} title={item.id === user?.id ? "Không thể tự vô hiệu hóa tài khoản" : undefined} onClick={() => setTarget(item)}>{item.status === "ACTIVE" ? "Vô hiệu hóa" : "Kích hoạt"}</Button></Td></tr>)}</Table>{query.data && <div className="mt-5"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}</AsyncStateView></Card><ConfirmDialog open={Boolean(target)} onClose={() => setTarget(null)} onConfirm={() => void confirm()} title={target?.status === "ACTIVE" ? "Vô hiệu hóa tài khoản" : "Kích hoạt tài khoản"} description={`Xác nhận thay đổi trạng thái của ${target?.fullName ?? "tài khoản này"}. Backend sẽ bảo vệ tài khoản admin cuối cùng.`} danger={target?.status === "ACTIVE"} loading={update.isPending} /></div>;
}
