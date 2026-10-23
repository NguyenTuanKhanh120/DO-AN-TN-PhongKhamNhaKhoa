import { HeartPulse } from "lucide-react";

export function BrandLogo({ inverse = false, compact = false }: { inverse?: boolean; compact?: boolean }) {
  return (
    <div className="flex items-center gap-3">
      <span className={`grid h-10 w-10 place-items-center rounded-xl ${inverse ? "bg-white text-primary-700" : "bg-primary-700 text-white"}`}><HeartPulse className="h-5 w-5" /></span>
      {!compact && <span><strong className={`block text-sm font-bold ${inverse ? "text-white" : "text-ink"}`}>Nha Khoa An Tâm</strong><small className={`block text-[9px] font-semibold tracking-wider ${inverse ? "text-white/75" : "text-primary-700"}`}>AN TÂM TỪ NỤ CƯỜI</small></span>}
    </div>
  );
}
