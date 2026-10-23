export type Role = "ADMIN" | "DOCTOR" | "STAFF" | "PATIENT";
export type UserStatus = "ACTIVE" | "INACTIVE";
export type DoctorStatus = "ACTIVE" | "INACTIVE" | "ON_LEAVE";
export type Gender = "MALE" | "FEMALE" | "OTHER" | "UNSPECIFIED";
export type AppointmentStatus =
  | "BOOKED"
  | "CHECKED_IN"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "NO_SHOW";
export type PaymentStatus = "DRAFT" | "UNPAID" | "PAID";
export type PaymentMethod = "CASH" | "BANK_TRANSFER";
export type ScheduleSession = "MORNING" | "AFTERNOON";
export type CalendarAvailabilityStatus = "AVAILABLE" | "FULL" | "NOT_WORKING" | "UNAVAILABLE";
export type AuthSessionStatus = "ACTIVE" | "LOCKED" | "REVOKED";
export type AppointmentAction =
  | "BOOK"
  | "RESCHEDULE"
  | "CANCEL"
  | "CHECK_IN"
  | "START_EXAM"
  | "COMPLETE"
  | "NO_SHOW";

export interface AuthResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresInSeconds: number;
  userId: string;
  email: string;
  fullName: string;
  role: Role;
}

export interface AuthSession {
  sessionId: string;
  role: Role;
  status: AuthSessionStatus;
  loggedInAt: string;
  absoluteExpiresAt: string;
  lastActivityAt: string;
  lockedAt: string | null;
  idleTimeoutSeconds: number | null;
  idleLockEnabled: boolean;
}

export interface Doctor {
  id: string;
  fullName: string;
  specialization: string;
  licenseNumber: string;
  bio: string | null;
  rating: number;
  experienceYears: number;
  workingStatus: DoctorStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AdminDoctor extends Doctor {
  userId: string;
  email: string;
  accountStatus: UserStatus;
}

export interface DentalService {
  id: string;
  name: string;
  description: string | null;
  price: number;
  imageUrl: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DoctorSchedule {
  id: string;
  doctorId: string;
  dayOfWeek: number;
  session: ScheduleSession;
  startTime: string;
  endTime: string;
  createdAt: string;
  updatedAt: string;
}

export interface AvailableSlot {
  date: string;
  session: ScheduleSession;
  startTime: string;
  endTime: string;
  startAt: string;
  endAt: string;
}

export interface CalendarSlot {
  startAt: string;
  endAt: string;
}

export interface DoctorCalendarSession {
  session: ScheduleSession;
  status: CalendarAvailabilityStatus;
  availableSlotCount: number;
  availableSlots: CalendarSlot[];
}

export interface DoctorCalendarDay {
  date: string;
  dayOfWeek: number;
  status: CalendarAvailabilityStatus;
  sessions: DoctorCalendarSession[];
}

export interface DoctorCalendar {
  doctorId: string;
  serviceId: string;
  fromDate: string;
  toDate: string;
  days: DoctorCalendarDay[];
}

export interface Appointment {
  id: string;
  patientId: string;
  patientFullName: string;
  patientPhone: string | null;
  doctorId: string;
  doctorFullName: string;
  doctorSpecialization: string;
  serviceId: string;
  serviceName: string;
  previousAppointmentId: string | null;
  scheduledStartAt: string;
  scheduledEndAt: string;
  status: AppointmentStatus;
  checkedInAt: string | null;
  actualStartAt: string | null;
  actualEndAt: string | null;
  cancellationReason: string | null;
  cancellationSource: string | null;
  cancelledAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BookAppointmentResult {
  id: string;
  patientId: string;
  doctorId: string;
  serviceId: string;
  previousAppointmentId: string | null;
  scheduledStartAt: string;
  scheduledEndAt: string;
  status: AppointmentStatus;
  createdAt: string;
}

export interface AppointmentHistory {
  id: string;
  appointmentId: string;
  action: AppointmentAction;
  changedByUserId: string;
  changedByFullName: string;
  changedByRole: Role;
  reason: string | null;
  details: Record<string, unknown> | null;
  createdAt: string;
}

export interface PrescriptionItem {
  medicationName: string;
  dosage: string;
  quantity: number;
  instructions: string;
}

export interface MedicalRecord {
  id: string;
  appointmentId: string;
  diagnosis: string;
  treatment: string;
  treatmentPlan: string | null;
  notes: string | null;
  prescription: PrescriptionItem[];
  followUpRequired: boolean;
  recommendedFollowUpDate: string | null;
  followUpNote: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PatientMedicalRecord extends MedicalRecord {
  doctorId: string;
  doctorFullName: string;
  doctorSpecialization: string;
  serviceId: string;
  serviceName: string;
  scheduledStartAt: string;
  actualStartAt: string | null;
  actualEndAt: string | null;
}

export interface PaymentItem {
  serviceId: string;
  serviceName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  additional: boolean;
}

export interface Payment {
  id: string;
  appointmentId: string;
  appointmentStatus: AppointmentStatus;
  scheduledStartAt: string | null;
  patientId: string;
  patientName: string;
  patientPhone: string | null;
  doctorId: string;
  doctorName: string;
  originalServiceId: string;
  originalServiceName: string;
  items: PaymentItem[];
  totalAmount: number;
  status: PaymentStatus;
  paymentMethod: PaymentMethod | null;
  confirmedById: string | null;
  confirmedByName: string | null;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PatientProfile {
  patientId: string;
  userId: string;
  email: string;
  fullName: string;
  dateOfBirth: string | null;
  gender: Gender;
  phone: string | null;
  address: string | null;
  medicalHistory: string | null;
  allergies: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUser {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

export interface DashboardSummary {
  totalPatients: number;
  totalDoctors: number;
  todayAppointments: number;
  completedToday: number;
  cancelledToday: number;
  noShowToday: number;
}

export interface RevenueSummary {
  todayRevenue: number;
  rangeFrom: string;
  rangeTo: string;
  rangeRevenue: number;
  month: string;
  monthlyRevenue: number;
  totalPaidPayments: number;
  totalUnpaidPayments: number;
  totalUnpaidAmount: number;
}
