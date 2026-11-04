import { CalendarPlus } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { ServiceImage } from "@/components/common/ServiceImage";
import { Button } from "@/components/ui/Button";
import type { DentalService } from "@/types/domain";
import { formatMoney } from "@/utils/format";
import { buildBookingUrl } from "../utils/navigation";

export function ServiceSuggestionCard({ service }: { service: DentalService }) {
  const navigate = useNavigate();
  return (
    <article className="overflow-hidden rounded-xl border border-primary-100 bg-white">
      <div className="flex gap-3 p-3">
        <ServiceImage src={service.imageUrl} alt={service.name} className="h-16 w-20 shrink-0 rounded-lg" />
        <div className="min-w-0 flex-1">
          <h4 className="text-sm font-bold text-ink">{service.name}</h4>
          <p className="mt-1 line-clamp-2 text-xs text-muted">{service.description || "Dịch vụ đang được phòng khám cung cấp."}</p>
          <p className="mt-1 text-xs font-bold text-primary-700">{formatMoney(service.price)}</p>
        </div>
      </div>
      <div className="border-t border-slate-100 p-2">
        <Button
          size="sm"
          variant="secondary"
          className="w-full"
          onClick={() => navigate(buildBookingUrl({ serviceId: service.id }))}
        >
          <CalendarPlus className="h-4 w-4" /> Chọn dịch vụ này
        </Button>
      </div>
    </article>
  );
}
