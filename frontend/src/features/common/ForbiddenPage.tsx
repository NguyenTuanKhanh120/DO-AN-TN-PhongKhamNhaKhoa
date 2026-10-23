import { ShieldX } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/contexts/AuthContext";
import { roleHome } from "@/routes/role-home";

export default function ForbiddenPage() {
  const { user } = useAuth();
  return <div className="grid min-h-screen place-items-center bg-slate-50 p-5"><div className="max-w-md text-center"><span className="mx-auto grid h-16 w-16 place-items-center rounded-2xl bg-red-50 text-red-600"><ShieldX className="h-8 w-8" /></span><h1 className="mt-5 text-3xl font-extrabold text-ink">Bạn không có quyền truy cập</h1><p className="mt-2 text-muted">Tài khoản hiện tại không được phép mở chức năng này.</p><Link to={user ? roleHome[user.role] : "/login"}><Button className="mt-6">Trở về trang phù hợp</Button></Link></div></div>;
}
