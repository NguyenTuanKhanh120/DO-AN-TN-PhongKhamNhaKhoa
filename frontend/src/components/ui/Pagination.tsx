import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "./Button";

export function Pagination({ page, totalPages, totalElements, onPageChange }: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) return <p className="text-sm text-muted">{totalElements} kết quả</p>;
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-muted">Trang {page + 1}/{totalPages} · {totalElements} kết quả</p>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPageChange(page - 1)} aria-label="Trang trước">
          <ChevronLeft className="h-4 w-4" /> Trước
        </Button>
        <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)} aria-label="Trang sau">
          Sau <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
