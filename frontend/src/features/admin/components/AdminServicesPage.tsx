import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { ServiceImage } from "@/components/common/ServiceImage";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { FormField } from "@/components/ui/FormField";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Pagination } from "@/components/ui/Pagination";
import { Textarea } from "@/components/ui/Textarea";
import { useToast } from "@/contexts/ToastContext";
import { useServices } from "@/features/public/hooks/usePublicData";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { getApiErrorMessage } from "@/services/api/errors";
import type { DentalService } from "@/types/domain";
import { formatMoney } from "@/utils/format";
import { useServiceMutations } from "../hooks/useAdminData";
import { SERVICE_IMAGE_ACCEPT, serviceSchema, type ServiceFormValues } from "../utils/validation";

const emptyService: DentalService = {
  id: "",
  name: "",
  description: "",
  price: 0,
  imageUrl: null,
  active: true,
  createdAt: "",
  updatedAt: "",
};

const emptyForm: ServiceFormValues = { name: "", description: "", price: 0, imageUrl: "", imageFile: null };

export default function AdminServicesPage() {
  const [active, setActive] = useState(true);
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<DentalService | null>(null);
  const [statusTarget, setStatusTarget] = useState<DentalService | null>(null);
  const { showToast } = useToast();
  const query = useServices({ active, search: useDebouncedValue(search), page, size: 20 });
  const mutations = useServiceMutations();
  const form = useForm<ServiceFormValues>({ resolver: zodResolver(serviceSchema), defaultValues: emptyForm });
  const fileInputRef = useRef<HTMLInputElement>(null);
  const imageUrl = form.watch("imageUrl");
  const imageFile = form.watch("imageFile");
  const [filePreviewUrl, setFilePreviewUrl] = useState<string | null>(null);
  const parsedImageUrl = serviceSchema.shape.imageUrl.safeParse(imageUrl);
  const previewUrl = filePreviewUrl ?? (parsedImageUrl.success && parsedImageUrl.data ? parsedImageUrl.data : null);

  useEffect(() => {
    if (!imageFile) {
      setFilePreviewUrl(null);
      return;
    }
    const objectUrl = URL.createObjectURL(imageFile);
    setFilePreviewUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [imageFile]);

  useEffect(() => {
    if (editing) {
      form.reset({
        name: editing.name,
        description: editing.description ?? "",
        price: editing.price,
        imageUrl: editing.imageUrl ?? "",
        imageFile: null,
      });
    }
  }, [editing, form]);

  const resetFileInput = () => {
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const closeEditor = () => {
    setEditing(null);
    form.reset(emptyForm);
    resetFileInput();
  };

  const openCreate = () => {
    setEditing(emptyService);
    form.reset(emptyForm);
    resetFileInput();
  };

  const submit = form.handleSubmit(async (values) => {
    if (!editing) return;
    try {
      const image = values.imageFile
        ? await mutations.upload.mutateAsync(values.imageFile)
        : null;
      const payload = {
        name: values.name,
        price: values.price,
        description: values.description || null,
        imageUrl: image?.imageUrl ?? (values.imageUrl || null),
      };
      if (editing.id) await mutations.update.mutateAsync({ id: editing.id, payload });
      else await mutations.create.mutateAsync(payload);
      showToast("Lưu dịch vụ thành công", "success");
      closeEditor();
    } catch (error) {
      form.setError("root", { message: getApiErrorMessage(error) });
    }
  });

  const changeStatus = async () => {
    if (!statusTarget) return;
    try {
      await mutations.status.mutateAsync({ id: statusTarget.id, active: !statusTarget.active });
      showToast("Cập nhật trạng thái dịch vụ thành công", "success");
      setStatusTarget(null);
    } catch (error) {
      showToast(getApiErrorMessage(error), "error");
    }
  };

  return (
    <div>
      <PageHeader
        title="Quản lý dịch vụ"
        description="Quản lý nội dung, giá và ảnh minh họa cho từng dịch vụ."
        action={<Button onClick={openCreate}><Plus className="h-4 w-4" /> Thêm dịch vụ</Button>}
      />
      <div className="mb-4 flex gap-2 rounded-xl bg-slate-100 p-1 sm:w-fit">
        <button onClick={() => { setActive(true); setPage(0); }} className={`rounded-lg px-4 py-2 text-sm font-semibold ${active ? "bg-white text-primary-700 shadow" : "text-slate-600"}`}>Đang hoạt động</button>
        <button onClick={() => { setActive(false); setPage(0); }} className={`rounded-lg px-4 py-2 text-sm font-semibold ${!active ? "bg-white text-primary-700 shadow" : "text-slate-600"}`}>Đã tắt</button>
      </div>
      <div className="filter-bar mb-6">
        <Input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} icon={<Search className="h-4 w-4" />} placeholder="Tìm dịch vụ" />
      </div>
      <Card className="p-4 sm:p-5">
        <AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length}>
          <div className="grid gap-4 lg:grid-cols-2">
            {query.data?.content.map((service) => (
              <div key={service.id} className="flex gap-4 rounded-xl border border-slate-200 p-4">
                <ServiceImage src={service.imageUrl} alt={service.name} className="h-24 w-28 shrink-0 rounded-xl border border-slate-100" />
                <div className="min-w-0 flex-1">
                  <div className="flex justify-between gap-3">
                    <div className="min-w-0">
                      <h2 className="truncate font-bold">{service.name}</h2>
                      <p className="mt-1 line-clamp-2 text-sm text-muted">{service.description || "Chưa có mô tả."}</p>
                    </div>
                    <StatusBadge status={service.active ? "ACTIVE" : "INACTIVE"} />
                  </div>
                  <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                    <strong className="text-primary-700">{formatMoney(service.price)}</strong>
                    <div className="flex gap-2">
                      <Button size="sm" variant="outline" onClick={() => { resetFileInput(); setEditing(service); }}>Sửa</Button>
                      <Button size="sm" variant={service.active ? "danger" : "secondary"} onClick={() => setStatusTarget(service)}>{service.active ? "Tắt" : "Bật"}</Button>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
          {query.data && <div className="mt-5"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}
        </AsyncStateView>
      </Card>
      <Modal open={Boolean(editing)} onClose={closeEditor} title={editing?.id ? "Cập nhật dịch vụ" : "Thêm dịch vụ"}>
        <form className="space-y-4" onSubmit={submit}>
          {form.formState.errors.root?.message && <p className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{form.formState.errors.root.message}</p>}
          <FormField label="Tên dịch vụ" required error={form.formState.errors.name?.message}>
            <Input {...form.register("name")} />
          </FormField>
          <FormField label="Mô tả">
            <Textarea {...form.register("description")} />
          </FormField>
          <FormField label="Giá (VND)" required error={form.formState.errors.price?.message}>
            <Input type="number" min="0" step="1000" {...form.register("price")} />
          </FormField>
          <FormField
            label="Chọn ảnh từ máy"
            error={form.formState.errors.imageFile?.message}
            hint="Hỗ trợ JPEG, PNG hoặc WebP; dung lượng tối đa 5 MB."
          >
            <input
              ref={fileInputRef}
              type="file"
              accept={SERVICE_IMAGE_ACCEPT}
              onChange={(event) => {
                form.setValue("imageFile", event.target.files?.[0] ?? null, { shouldDirty: true, shouldValidate: true });
              }}
              className="block w-full cursor-pointer rounded-xl border border-line bg-white text-sm text-slate-600 file:mr-4 file:border-0 file:bg-primary-50 file:px-4 file:py-3 file:font-semibold file:text-primary-700 hover:file:bg-primary-100"
            />
          </FormField>
          {imageFile && (
            <div className="flex items-center justify-between gap-3 rounded-xl bg-primary-50 px-3 py-2 text-sm text-primary-800">
              <span className="min-w-0 truncate">{imageFile.name} · {(imageFile.size / 1024 / 1024).toFixed(2)} MB</span>
              <button
                type="button"
                aria-label="Bỏ ảnh đã chọn"
                className="shrink-0 rounded-lg p-1 hover:bg-primary-100"
                onClick={() => {
                  form.setValue("imageFile", null, { shouldDirty: true, shouldValidate: true });
                  resetFileInput();
                }}
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          )}
          <FormField
            label="Hoặc dán URL ảnh"
            error={form.formState.errors.imageUrl?.message}
            hint={imageFile ? "Ảnh đã chọn từ máy sẽ được ưu tiên khi lưu." : "Dùng đường dẫn ảnh công khai bắt đầu bằng http:// hoặc https://."}
          >
            <Input type="url" placeholder="https://example.com/anh-dich-vu.jpg" {...form.register("imageUrl")} />
          </FormField>
          {previewUrl && (
            <div>
              <p className="mb-2 text-sm font-medium text-ink">Xem trước ảnh</p>
              <ServiceImage src={previewUrl} alt={form.watch("name") || "Dịch vụ"} className="h-44 w-full rounded-xl border border-slate-200" />
            </div>
          )}
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={closeEditor}>Hủy</Button>
            <Button type="submit" loading={mutations.upload.isPending || mutations.create.isPending || mutations.update.isPending}>
              Lưu dịch vụ
            </Button>
          </div>
        </form>
      </Modal>
      <ConfirmDialog
        open={Boolean(statusTarget)}
        onClose={() => setStatusTarget(null)}
        onConfirm={() => void changeStatus()}
        title={statusTarget?.active ? "Tắt dịch vụ" : "Bật dịch vụ"}
        description={`Xác nhận thay đổi trạng thái dịch vụ ${statusTarget?.name ?? ""}.`}
        danger={statusTarget?.active}
        loading={mutations.status.isPending}
      />
    </div>
  );
}
