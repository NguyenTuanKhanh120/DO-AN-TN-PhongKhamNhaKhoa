import { Badge } from "@/components/ui/Badge";
import type { AppointmentStatus, DoctorStatus, PaymentStatus, UserStatus } from "@/types/domain";
import { appointmentStatusLabels, doctorStatusLabels, paymentStatusLabels, userStatusLabels } from "@/utils/format";

type Status = AppointmentStatus | DoctorStatus | PaymentStatus | UserStatus;

function toneFor(status: Status): "success" | "warning" | "danger" | "info" | "neutral" | "primary" {
  if (["ACTIVE", "COMPLETED", "PAID"].includes(status)) return "success";
  if (["IN_PROGRESS", "CHECKED_IN"].includes(status)) return "info";
  if (["BOOKED", "UNPAID", "ON_LEAVE"].includes(status)) return "warning";
  if (["CANCELLED", "NO_SHOW", "INACTIVE"].includes(status)) return "danger";
  return "neutral";
}

export function StatusBadge({ status }: { status: Status }) {
  const label =
    status in appointmentStatusLabels
      ? appointmentStatusLabels[status as AppointmentStatus]
      : status in paymentStatusLabels
        ? paymentStatusLabels[status as PaymentStatus]
        : status === "ON_LEAVE"
          ? doctorStatusLabels[status]
          : userStatusLabels[status as UserStatus];
  return <Badge tone={toneFor(status)}>{label}</Badge>;
}
