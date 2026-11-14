import { useState } from "react";
import { AsyncStateView } from "@/components/common/AsyncStateView";
import { Card } from "@/components/ui/Card";
import { FormField } from "@/components/ui/FormField";
import { Input } from "@/components/ui/Input";
import { clinicToday } from "@/utils/date";
import { formatMoney } from "@/utils/format";
import { useRevenue } from "../hooks/useAdminData";

type RevenuePeriod = "DAY" | "MONTH" | "YEAR";

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const MONTH_PATTERN = /^\d{4}-\d{2}$/;
const YEAR_PATTERN = /^\d{4}$/;

const periodOptions: Array<{ value: RevenuePeriod; label: string }> = [
  { value: "DAY", label: "Theo ngày" },
  { value: "MONTH", label: "Theo tháng" },
  { value: "YEAR", label: "Theo năm" },
];

export function AdminRevenueSummary() {
  const today = clinicToday();
  const [period, setPeriod] = useState<RevenuePeriod>("DAY");
  const [selectedDate, setSelectedDate] = useState(today);
  const currentMonth = today.slice(0, 7);
  const currentYear = today.slice(0, 4);
  const [selectedMonth, setSelectedMonth] = useState(currentMonth);
  const [selectedYear, setSelectedYear] = useState(currentYear);

  const selectionFormatValid =
    period === "DAY"
      ? DATE_PATTERN.test(selectedDate)
      : period === "MONTH"
        ? MONTH_PATTERN.test(selectedMonth)
        : YEAR_PATTERN.test(selectedYear);
  const selectionInFuture =
    period === "DAY"
      ? selectedDate > today
      : period === "MONTH"
        ? selectedMonth > currentMonth
        : selectedYear > currentYear;
  const selectionValid = selectionFormatValid && !selectionInFuture;

  const params =
    period === "DAY"
      ? { fromDate: selectedDate, toDate: selectedDate }
      : period === "MONTH"
        ? { month: selectedMonth }
        : {
            fromDate: `${selectedYear}-01-01`,
            toDate: `${selectedYear}-12-31`,
          };

  const revenue = useRevenue(selectionValid ? params : {}, selectionValid);
  const totalRevenue =
    period === "MONTH"
      ? revenue.data?.monthlyRevenue
      : revenue.data?.rangeRevenue;

  return (
    <Card className="mt-6 p-5 sm:p-6">
      <h2 className="text-lg font-bold text-ink">Tổng doanh thu</h2>
      <div className="mt-5 flex gap-2 rounded-xl bg-slate-100 p-1 sm:w-fit">
        {periodOptions.map((option) => (
          <button
            key={option.value}
            type="button"
            aria-pressed={period === option.value}
            onClick={() => setPeriod(option.value)}
            className={`rounded-lg px-4 py-2 text-sm font-semibold transition ${
              period === option.value
                ? "bg-white text-primary-700 shadow"
                : "text-slate-600 hover:text-ink"
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>

      <div className="mt-5 max-w-xl rounded-xl border border-slate-200 p-4">
        {period === "DAY" && (
          <FormField
            label="Chọn ngày"
            error={selectionInFuture ? "Không thể chọn ngày trong tương lai" : undefined}
          >
            <Input
              type="date"
              max={today}
              error={selectionInFuture}
              value={selectedDate}
              onChange={(event) => setSelectedDate(event.target.value)}
            />
          </FormField>
        )}
        {period === "MONTH" && (
          <FormField
            label="Chọn tháng"
            error={selectionInFuture ? "Không thể chọn tháng trong tương lai" : undefined}
          >
            <Input
              type="month"
              max={currentMonth}
              error={selectionInFuture}
              value={selectedMonth}
              onChange={(event) => setSelectedMonth(event.target.value)}
            />
          </FormField>
        )}
        {period === "YEAR" && (
          <FormField
            label="Chọn năm"
            error={selectionInFuture ? "Không thể chọn năm trong tương lai" : undefined}
          >
            <Input
              type="number"
              min="2000"
              max={currentYear}
              step="1"
              error={selectionInFuture}
              value={selectedYear}
              onChange={(event) => setSelectedYear(event.target.value)}
            />
          </FormField>
        )}

        <div className="mt-4">
          <AsyncStateView
            loading={revenue.isLoading}
            error={revenue.error}
            empty={!selectionValid || totalRevenue === undefined}
            emptyTitle={selectionInFuture ? "Không thể xem doanh thu tương lai" : "Chọn thời gian hợp lệ"}
            emptyDescription={selectionInFuture ? "Vui lòng chọn ngày, tháng hoặc năm hiện tại hoặc trong quá khứ." : "Doanh thu sẽ hiển thị sau khi chọn thời gian."}
            onRetry={() => void revenue.refetch()}
          >
            <div className="rounded-xl bg-primary-50 p-4">
              <p className="text-sm font-semibold text-primary-700">
                Tổng doanh thu
              </p>
              <p className="mt-1 text-2xl font-bold text-ink">
                {formatMoney(totalRevenue ?? 0)}
              </p>
            </div>
          </AsyncStateView>
        </div>
      </div>
    </Card>
  );
}
