import { CalendarDays, Star, Stethoscope } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import type { Doctor } from "@/types/domain";
import { buildBookingUrl } from "../utils/navigation";

export function DoctorSuggestionCard({ doctor, serviceId }: { doctor: Doctor; serviceId?: string }) {
  const navigate = useNavigate();
  const target = serviceId
    ? buildBookingUrl({ serviceId, doctorId: doctor.id })
    : `/doctors/${doctor.id}`;
  return (
    <article className="rounded-xl border border-primary-100 bg-white p-3">
      <div className="flex items-start gap-3">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-primary-50 text-primary-700">
          <Stethoscope className="h-5 w-5" />
        </span>
        <div className="min-w-0 flex-1">
          <h4 className="text-sm font-bold text-ink">{doctor.fullName}</h4>
          <p className="mt-0.5 text-xs text-muted">{doctor.specialization}</p>
          <p className="mt-1 flex items-center gap-1 text-xs text-amber-600">
            <Star className="h-3.5 w-3.5 fill-current" /> {doctor.rating.toFixed(1)} · {doctor.experienceYears} năm kinh nghiệm
          </p>
        </div>
      </div>
      <Button size="sm" variant="outline" className="mt-3 w-full" onClick={() => navigate(target)}>
        <CalendarDays className="h-4 w-4" /> {serviceId ? "Xem lịch phù hợp" : "Xem thông tin bác sĩ"}
      </Button>
    </article>
  );
}
