import {
  CalendarDays,
  CircleDollarSign,
  ClipboardList,
  LayoutDashboard,
  LogOut,
  Menu,
  Stethoscope,
  UserRound,
  Users,
  X,
  type LucideIcon,
} from "lucide-react";
import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { BrandLogo } from "@/components/common/BrandLogo";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/contexts/AuthContext";
import type { Role } from "@/types/domain";
import { cn } from "@/utils/cn";
import { roleLabels } from "@/utils/format";

interface NavItem { to: string; label: string; icon: LucideIcon }

const navByRole: Record<Role, NavItem[]> = {
  PATIENT: [
    { to: "/patient/dashboard", label: "Tổng quan", icon: LayoutDashboard },
    { to: "/patient/book-appointment", label: "Đặt lịch khám", icon: CalendarDays },
    { to: "/patient/appointments", label: "Lịch khám", icon: ClipboardList },
    { to: "/patient/medical-records", label: "Bệnh án", icon: Stethoscope },
    { to: "/patient/payments", label: "Thanh toán", icon: CircleDollarSign },
    { to: "/patient/profile", label: "Hồ sơ cá nhân", icon: UserRound },
  ],
  DOCTOR: [
    { to: "/doctor/dashboard", label: "Tổng quan", icon: LayoutDashboard },
    { to: "/doctor/appointments", label: "Lịch khám", icon: ClipboardList },
    { to: "/doctor/schedules", label: "Lịch làm việc", icon: CalendarDays },
  ],
  STAFF: [
    { to: "/staff/dashboard", label: "Tổng quan", icon: LayoutDashboard },
    { to: "/staff/appointments", label: "Lịch toàn phòng khám", icon: CalendarDays },
    { to: "/staff/payments", label: "Thu tiền", icon: CircleDollarSign },
  ],
  ADMIN: [
    { to: "/admin/dashboard", label: "Tổng quan", icon: LayoutDashboard },
    { to: "/admin/users", label: "Tài khoản", icon: Users },
    { to: "/admin/staff", label: "Nhân viên", icon: UserRound },
    { to: "/admin/doctors", label: "Bác sĩ", icon: Stethoscope },
    { to: "/admin/services", label: "Dịch vụ", icon: ClipboardList },
    { to: "/admin/appointments", label: "Lịch khám", icon: CalendarDays },
    { to: "/admin/payments", label: "Thanh toán", icon: CircleDollarSign },
  ],
};

export function DashboardLayout() {
  const { user, signOut } = useAuth();
  const [open, setOpen] = useState(false);
  if (!user) return null;
  const sidebar = (
    <div className="flex h-full flex-col">
      <div className="border-b border-white/10 p-5"><BrandLogo inverse /></div>
      <nav className="flex-1 space-y-1 overflow-y-auto p-3">
        {navByRole[user.role].map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} onClick={() => setOpen(false)} className={({ isActive }) => cn("flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition", isActive ? "bg-white text-primary-800 shadow" : "text-white/75 hover:bg-white/10 hover:text-white")}><Icon className="h-5 w-5" />{label}</NavLink>)}
      </nav>
      <div className="border-t border-white/10 p-4"><div className="mb-3 px-2"><p className="truncate text-sm font-semibold text-white">{user.fullName}</p><p className="text-xs text-white/60">{roleLabels[user.role]}</p></div><Button variant="ghost" className="w-full justify-start text-white hover:bg-white/10 hover:text-white" onClick={() => void signOut()}><LogOut className="h-4 w-4" /> Đăng xuất</Button></div>
    </div>
  );
  return (
    <div className="min-h-screen bg-slate-50 lg:grid lg:grid-cols-[256px_1fr]">
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-64 bg-gradient-to-b from-primary-800 to-cyanbrand lg:block">{sidebar}</aside>
      {open && <div className="fixed inset-0 z-50 lg:hidden"><button className="absolute inset-0 bg-slate-950/50" onClick={() => setOpen(false)} aria-label="Đóng menu" /><aside className="relative h-full w-72 bg-gradient-to-b from-primary-800 to-cyanbrand shadow-2xl">{sidebar}<button className="absolute right-3 top-3 rounded-lg p-2 text-white/80" onClick={() => setOpen(false)} aria-label="Đóng menu"><X /></button></aside></div>}
      <div className="lg:col-start-2">
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6 lg:px-8"><button className="rounded-lg p-2 lg:hidden" onClick={() => setOpen(true)} aria-label="Mở menu"><Menu /></button><div className="ml-auto text-right"><p className="text-sm font-semibold text-ink">{user.fullName}</p><p className="text-xs text-muted">{roleLabels[user.role]}</p></div></header>
        <main className="p-4 sm:p-6 lg:p-8"><Outlet /></main>
      </div>
    </div>
  );
}
