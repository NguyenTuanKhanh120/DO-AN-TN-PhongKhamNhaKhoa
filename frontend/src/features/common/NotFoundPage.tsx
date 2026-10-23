import { Link } from "react-router-dom";
import { Button } from "@/components/ui/Button";

export default function NotFoundPage() {
  return <div className="grid min-h-screen place-items-center bg-slate-50 p-5 text-center"><div><p className="text-7xl font-extrabold text-primary-100">404</p><h1 className="mt-3 text-2xl font-bold text-ink">Không tìm thấy trang</h1><p className="mt-2 text-muted">Đường dẫn bạn mở không tồn tại hoặc đã được thay đổi.</p><Link to="/"><Button className="mt-6">Về trang dịch vụ</Button></Link></div></div>;
}
