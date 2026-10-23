import { Menu, X } from "lucide-react";
import { useState } from "react";
import { Link, NavLink, Outlet } from "react-router-dom";
import { BrandLogo } from "@/components/common/BrandLogo";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/contexts/AuthContext";
import { cn } from "@/utils/cn";
import { roleHome } from "@/routes/role-home";

const publicLinks = [
  { to: "/doctors", label: "Đội ngũ bác sĩ" },
  { to: "/services", label: "Dịch vụ" },
];

export function PublicLayout() {
  const [open, setOpen] = useState(false);
  const { user } = useAuth();
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="page-container flex h-18 items-center justify-between py-3">
          <Link to="/"><BrandLogo /></Link>
          <nav className="hidden items-center gap-7 md:flex">
            {publicLinks.map((item) => <NavLink key={item.to} to={item.to} className={({ isActive }) => cn("text-sm font-semibold text-slate-600 hover:text-primary-700", isActive && "text-primary-700")}>{item.label}</NavLink>)}
            <Link to={user ? roleHome[user.role] : "/login"}><Button size="sm">{user ? "Vào hệ thống" : "Đăng nhập"}</Button></Link>
          </nav>
          <button className="rounded-lg p-2 md:hidden" onClick={() => setOpen((value) => !value)} aria-label="Mở menu">{open ? <X /> : <Menu />}</button>
        </div>
        {open && <nav className="page-container grid gap-2 border-t border-slate-100 py-4 md:hidden">{publicLinks.map((item) => <NavLink key={item.to} to={item.to} onClick={() => setOpen(false)} className="rounded-lg px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-primary-50">{item.label}</NavLink>)}<Link to={user ? roleHome[user.role] : "/login"}><Button className="mt-2 w-full">{user ? "Vào hệ thống" : "Đăng nhập"}</Button></Link></nav>}
      </header>
      <main><Outlet /></main>
      <footer className="border-t border-slate-200 bg-white py-10"><div className="page-container flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center"><BrandLogo /><p className="text-sm text-muted">© {new Date().getFullYear()} Nha Khoa An Tâm. Bảo lưu mọi quyền.</p></div></footer>
    </div>
  );
}
