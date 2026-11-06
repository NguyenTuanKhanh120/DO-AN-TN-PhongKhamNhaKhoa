import type {
  AppointmentAction,
  AppointmentStatus,
  AuthSessionStatus,
  CalendarAvailabilityStatus,
  DoctorStatus,
  Gender,
  PaymentMethod,
  PaymentStatus,
  Role,
  ScheduleSession,
  UserStatus,
} from "@/types/domain";

const dateTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
  timeZone: "Asia/Ho_Chi_Minh",
  dateStyle: "medium",
  timeStyle: "short",
});

const dateFormatter = new Intl.DateTimeFormat("vi-VN", {
  timeZone: "Asia/Ho_Chi_Minh",
  dateStyle: "medium",
});

const moneyFormatter = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
});

export const formatDateTime = (value?: string | null): string =>
  value ? dateTimeFormatter.format(new Date(value)) : "—";

export const formatDate = (value?: string | null): string =>
  value ? dateFormatter.format(new Date(`${value}T00:00:00+07:00`)) : "—";

export const formatMoney = (value?: number | null): string => moneyFormatter.format(value ?? 0);

export const formatTime = (value?: string | null): string => value?.slice(0, 5) ?? "—";

export const appointmentArrivalNotice =
  "Vui lòng có mặt trước giờ dự kiến khoảng 15 phút. Thời gian bắt đầu thực tế có thể sớm hoặc muộn hơn tùy thời lượng khám của ca trước.";

export const roleLabels: Record<Role, string> = {
  ADMIN: "Quản trị viên",
  DOCTOR: "Bác sĩ",
  STAFF: "Nhân viên",
  PATIENT: "Bệnh nhân",
};

export const userStatusLabels: Record<UserStatus, string> = {
  ACTIVE: "Đang hoạt động",
  INACTIVE: "Ngừng hoạt động",
};

export const doctorStatusLabels: Record<DoctorStatus, string> = {
  ACTIVE: "Đang làm việc",
  INACTIVE: "Ngừng làm việc",
  ON_LEAVE: "Tạm nghỉ",
};

export const appointmentStatusLabels: Record<AppointmentStatus, string> = {
  BOOKED: "Đã đặt lịch",
  CHECKED_IN: "Đã tiếp nhận",
  IN_PROGRESS: "Đang khám",
  COMPLETED: "Hoàn thành",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Không đến",
};

export const paymentStatusLabels: Record<PaymentStatus, string> = {
  DRAFT: "Đang lập phiếu",
  UNPAID: "Chưa thanh toán",
  PAID: "Đã thanh toán",
};

export const paymentMethodLabels: Record<PaymentMethod, string> = {
  CASH: "Tiền mặt",
  BANK_TRANSFER: "Chuyển khoản",
};

export const sessionLabels: Record<ScheduleSession, string> = {
  MORNING: "Buổi sáng",
  AFTERNOON: "Buổi chiều",
};

export const formatQueueLabel = (queueNumber?: number | null): string =>
  queueNumber ? `Ca ${queueNumber}` : "Ca chưa xác định";

export const formatSessionWindow = (
  session: ScheduleSession,
  startTime?: string | null,
  endTime?: string | null,
): string => `${sessionLabels[session]}${startTime && endTime ? ` ${formatTime(startTime)}–${formatTime(endTime)}` : ""}`;

export const formatAppointmentSchedule = (value: {
  appointmentDate: string;
  session: ScheduleSession | null;
  queueNumber: number | null;
  estimatedStartTime: string;
  estimatedStartAt: string;
}): string => {
  if (!value.session || !value.queueNumber) return formatDateTime(value.estimatedStartAt);
  return `${formatDate(value.appointmentDate)} · ${sessionLabels[value.session]} · ${formatQueueLabel(value.queueNumber)} · dự kiến ${formatTime(value.estimatedStartTime)}`;
};

export const calendarStatusLabels: Record<CalendarAvailabilityStatus, string> = {
  AVAILABLE: "Còn lịch",
  FULL: "Đã kín lịch",
  NOT_WORKING: "Không làm việc",
  UNAVAILABLE: "Chưa thể đặt",
};

export const genderLabels: Record<Gender, string> = {
  MALE: "Nam",
  FEMALE: "Nữ",
  OTHER: "Khác",
  UNSPECIFIED: "Chưa xác định",
};

export const authSessionStatusLabels: Record<AuthSessionStatus, string> = {
  ACTIVE: "Đang hoạt động",
  LOCKED: "Đã khóa",
  REVOKED: "Đã thu hồi",
};

export const actionLabels: Record<AppointmentAction, string> = {
  BOOK: "Đặt lịch",
  RESCHEDULE: "Đổi lịch",
  CANCEL: "Hủy lịch",
  CHECK_IN: "Tiếp nhận (dữ liệu cũ)",
  START_EXAM: "Bắt đầu khám",
  COMPLETE: "Hoàn thành khám",
  NO_SHOW: "Đánh dấu không đến",
};

export const weekDayLabels: Record<number, string> = {
  1: "Thứ Hai",
  2: "Thứ Ba",
  3: "Thứ Tư",
  4: "Thứ Năm",
  5: "Thứ Sáu",
  6: "Thứ Bảy",
  7: "Chủ Nhật",
};
