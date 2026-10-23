import { HeartHandshake, Minus, ShieldCheck } from "lucide-react";
import type { ReactNode } from "react";
import heroImage from "@/assets/images/dental-consultation-hero.png";
import { BrandLogo } from "@/components/common/BrandLogo";

export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-slate-50 lg:grid lg:grid-cols-[45.2%_54.8%]">
      <section className="relative hidden min-h-screen overflow-hidden bg-gradient-to-br from-primary-800 via-primary-700 to-cyanbrand px-[9%] py-8 text-white lg:flex lg:flex-col">
        <div className="pointer-events-none absolute -right-24 -top-40 h-96 w-96 rounded-full border border-white/10" />
        <div className="pointer-events-none absolute -right-8 -top-20 h-72 w-72 rounded-full border border-white/10" />
        <BrandLogo inverse />
        <div className="mt-[7vh]">
          <p className="flex items-center gap-2 text-[11px] font-semibold text-white/75"><Minus className="h-5 w-5" /> ĐỒNG HÀNH CÙNG NỤ CƯỜI VIỆT</p>
          <h1 className="mt-4 max-w-md text-[clamp(1.8rem,3vw,2.35rem)] font-extrabold leading-[1.22] tracking-tight">Chăm sóc nụ cười, nâng tầm sức khỏe</h1>
          <p className="mt-4 max-w-md text-sm leading-6 text-white/80">Đặt lịch khám tiện lợi, chủ động chăm sóc sức khỏe răng miệng cho bạn và gia đình.</p>
        </div>
        <img src={heroImage} alt="Bác sĩ Nha Khoa An Tâm tư vấn cho bệnh nhân" className="mt-6 aspect-[1.55/1] w-full rounded-2xl object-cover shadow-2xl" />
        <div className="mt-3 flex items-start gap-3"><span className="grid h-8 w-8 place-items-center rounded-lg bg-white/10"><HeartHandshake className="h-4 w-4" /></span><p className="text-[11px] leading-4"><strong className="block font-semibold">Chuyên tâm chăm sóc. An tâm mỗi ngày.</strong><span className="text-white/70">Mỗi nụ cười, kết nối mọi hành trình của bạn.</span></p></div>
        <p className="mt-auto pt-5 text-[10px] text-white/65">© {new Date().getFullYear()} Nha Khoa An Tâm. Bảo lưu mọi quyền.</p>
      </section>
      <section className="relative flex min-h-screen items-center justify-center px-5 py-12 sm:px-8">
        <p className="absolute right-7 top-7 hidden text-[10px] font-semibold tracking-wide text-slate-500 sm:block">CỔNG CHĂM SÓC NHA KHOA</p>
        <div className="w-full max-w-[360px]">
          <div className="mb-9 lg:hidden"><BrandLogo /></div>
          {children}
          <div className="mt-12 flex items-center justify-center gap-2 text-xs text-slate-500"><ShieldCheck className="h-4 w-4 text-primary-700" /> Thông tin của bạn luôn được bảo mật</div>
        </div>
        <p className="absolute bottom-7 hidden text-[10px] text-slate-400 sm:block">An tâm kết nối, trọn vẹn chăm sóc.</p>
      </section>
    </div>
  );
}
