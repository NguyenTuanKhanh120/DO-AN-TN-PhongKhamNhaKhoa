import { ArrowLeft, CalendarDays, Stethoscope } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { ServiceImage } from "@/components/common/ServiceImage";
import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { formatMoney } from "@/utils/format";
import { useService, useServiceDoctors } from "../hooks/usePublicData";

export default function ServiceDetailPage() {
  const { id } = useParams();
  const service = useService(id);
  const doctors = useServiceDoctors(id);

  return (
    <div className="page-container py-10 sm:py-14">
      <Link to="/services" className="mb-5 inline-flex items-center gap-2 text-sm font-semibold text-primary-700">
        <ArrowLeft className="h-4 w-4" /> Quay lại dịch vụ
      </Link>
      <AsyncStateView loading={service.isLoading} error={service.error} empty={!service.data} onRetry={() => void service.refetch()}>
        {service.data && (
          <>
            <Card className="grid overflow-hidden lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
              <ServiceImage src={service.data.imageUrl} alt={service.data.name} className="h-64 w-full lg:h-full lg:min-h-80" loading="eager" />
              <div className="p-6 sm:p-8">
                <span className="grid h-12 w-12 place-items-center rounded-2xl bg-primary-50 text-primary-700"><Stethoscope className="h-6 w-6" /></span>
                <h1 className="mt-5 text-3xl font-extrabold text-ink">{service.data.name}</h1>
                <p className="mt-4 max-w-3xl leading-7 text-muted">{service.data.description || "Liên hệ phòng khám để được tư vấn chi tiết về dịch vụ."}</p>
                <p className="mt-5 text-xl font-extrabold text-primary-700">{formatMoney(service.data.price)}</p>
              </div>
            </Card>
            <h2 className="mb-4 mt-8 text-xl font-bold text-ink">Bác sĩ thực hiện dịch vụ</h2>
            <AsyncStateView loading={doctors.isLoading} error={doctors.error} empty={!doctors.data?.length}>
              <div className="grid gap-4 md:grid-cols-2">
                {doctors.data?.map((doctor) => (
                  <Card key={doctor.id} className="flex items-center gap-4 p-5">
                    <span className="grid h-11 w-11 place-items-center rounded-xl bg-primary-50 text-primary-700"><Stethoscope className="h-5 w-5" /></span>
                    <div className="min-w-0 flex-1">
                      <Link to={`/doctors/${doctor.id}`} className="font-bold text-ink hover:text-primary-700">{doctor.fullName}</Link>
                      <p className="text-sm text-muted">{doctor.specialization}</p>
                    </div>
                    <StatusBadge status={doctor.workingStatus} />
                  </Card>
                ))}
              </div>
            </AsyncStateView>
            <div className="mt-7 flex justify-end">
              <Link to={`/patient/book-appointment?serviceId=${service.data.id}`}>
                <Button size="lg"><CalendarDays className="h-5 w-5" /> Đặt lịch dịch vụ</Button>
              </Link>
            </div>
          </>
        )}
      </AsyncStateView>
    </div>
  );
}
