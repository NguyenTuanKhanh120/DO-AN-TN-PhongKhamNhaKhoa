import { Search } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { PageHeader } from "@/components/common/PageHeader";
import { ServiceImage } from "@/components/common/ServiceImage";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Pagination } from "@/components/ui/Pagination";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { formatMoney } from "@/utils/format";
import { useServices } from "../hooks/usePublicData";

export default function ServicesPage() {
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const query = useServices({ search: useDebouncedValue(search), active: true, page, size: 12 });

  return (
    <div className="page-container py-10 sm:py-14">
      <PageHeader title="Dịch vụ nha khoa" description="Các dịch vụ chăm sóc răng miệng minh bạch và phù hợp cho cả gia đình." />
      <div className="filter-bar mb-6">
        <Input className="sm:max-w-md" value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} placeholder="Tìm dịch vụ" icon={<Search className="h-4 w-4" />} />
      </div>
      <AsyncStateView loading={query.isLoading} error={query.error} empty={!query.data?.content.length} onRetry={() => void query.refetch()} emptyTitle="Chưa tìm thấy dịch vụ">
        <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {query.data?.content.map((item) => (
            <Link key={item.id} to={`/services/${item.id}`}>
              <Card className="h-full overflow-hidden transition hover:border-primary-200 hover:shadow-soft">
                <ServiceImage src={item.imageUrl} alt={item.name} className="h-44 w-full" />
                <div className="p-5">
                  <h2 className="text-lg font-bold text-ink">{item.name}</h2>
                  <p className="mt-2 line-clamp-3 text-sm leading-6 text-muted">{item.description || "Liên hệ phòng khám để được tư vấn chi tiết."}</p>
                  <p className="mt-4 font-bold text-primary-700">{formatMoney(item.price)}</p>
                </div>
              </Card>
            </Link>
          ))}
        </div>
        {query.data && <div className="mt-6"><Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onPageChange={setPage} /></div>}
      </AsyncStateView>
    </div>
  );
}
