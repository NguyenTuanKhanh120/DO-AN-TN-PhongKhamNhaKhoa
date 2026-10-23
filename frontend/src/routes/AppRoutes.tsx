import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { DashboardLayout } from "@/components/layouts/DashboardLayout";
import { PublicLayout } from "@/components/layouts/PublicLayout";
import { ProtectedRoute, PublicOnlyRoute } from "./guards";

const LoginPage = lazy(() => import("@/features/auth/components/LoginPage"));
const RegisterPage = lazy(() => import("@/features/auth/components/RegisterPage"));
const DoctorsPage = lazy(() => import("@/features/public/components/DoctorsPage"));
const DoctorDetailPage = lazy(() => import("@/features/public/components/DoctorDetailPage"));
const ServicesPage = lazy(() => import("@/features/public/components/ServicesPage"));
const ServiceDetailPage = lazy(() => import("@/features/public/components/ServiceDetailPage"));
const PatientDashboardPage = lazy(() => import("@/features/patient/components/PatientDashboardPage"));
const PatientProfilePage = lazy(() => import("@/features/patient/components/PatientProfilePage"));
const BookingPage = lazy(() => import("@/features/patient/components/BookingPage"));
const AppointmentsPage = lazy(() => import("@/features/patient/components/AppointmentsPage"));
const AppointmentDetailPage = lazy(() => import("@/features/patient/components/AppointmentDetailPage"));
const MedicalRecordsPage = lazy(() => import("@/features/patient/components/MedicalRecordsPage"));
const MedicalRecordDetailPage = lazy(() => import("@/features/patient/components/MedicalRecordDetailPage"));
const PaymentsPage = lazy(() => import("@/features/patient/components/PaymentsPage"));
const PaymentDetailPage = lazy(() => import("@/features/patient/components/PaymentDetailPage"));
const DoctorDashboardPage = lazy(() => import("@/features/doctor/components/DoctorDashboardPage"));
const DoctorAppointmentsPage = lazy(() => import("@/features/doctor/components/DoctorAppointmentsPage"));
const DoctorAppointmentDetailPage = lazy(() => import("@/features/doctor/components/DoctorAppointmentDetailPage"));
const DoctorSchedulesPage = lazy(() => import("@/features/doctor/components/DoctorSchedulesPage"));
const StaffDashboardPage = lazy(() => import("@/features/staff/components/StaffDashboardPage"));
const StaffAppointmentsPage = lazy(() => import("@/features/staff/components/StaffAppointmentsPage"));
const StaffPaymentsPage = lazy(() => import("@/features/staff/components/StaffPaymentsPage"));
const StaffPaymentDetailPage = lazy(() => import("@/features/staff/components/StaffPaymentDetailPage"));
const AdminDashboardPage = lazy(() => import("@/features/admin/components/AdminDashboardPage"));
const AdminUsersPage = lazy(() => import("@/features/admin/components/AdminUsersPage"));
const AdminStaffPage = lazy(() => import("@/features/admin/components/AdminStaffPage"));
const AdminDoctorsPage = lazy(() => import("@/features/admin/components/AdminDoctorsPage"));
const AdminDoctorDetailPage = lazy(() => import("@/features/admin/components/AdminDoctorDetailPage"));
const AdminServicesPage = lazy(() => import("@/features/admin/components/AdminServicesPage"));
const AdminAppointmentsPage = lazy(() => import("@/features/admin/components/AdminAppointmentsPage"));
const AdminPaymentsPage = lazy(() => import("@/features/admin/components/AdminPaymentsPage"));
const ForbiddenPage = lazy(() => import("@/features/common/ForbiddenPage"));
const NotFoundPage = lazy(() => import("@/features/common/NotFoundPage"));

function LazyFallback() {
  return <div className="grid min-h-[50vh] place-items-center"><span className="h-10 w-10 animate-spin rounded-full border-4 border-primary-100 border-t-primary-700" aria-label="Đang tải trang" /></div>;
}

export function AppRoutes() {
  return <Suspense fallback={<LazyFallback />}><Routes>
    <Route path="/login" element={<PublicOnlyRoute><LoginPage /></PublicOnlyRoute>} />
    <Route path="/register" element={<PublicOnlyRoute><RegisterPage /></PublicOnlyRoute>} />
    <Route element={<PublicLayout />}>
      <Route index element={<Navigate to="/services" replace />} />
      <Route path="/doctors" element={<DoctorsPage />} />
      <Route path="/doctors/:id" element={<DoctorDetailPage />} />
      <Route path="/services" element={<ServicesPage />} />
      <Route path="/services/:id" element={<ServiceDetailPage />} />
    </Route>
    <Route element={<ProtectedRoute roles={["PATIENT"]}><DashboardLayout /></ProtectedRoute>}>
      <Route path="/patient/dashboard" element={<PatientDashboardPage />} />
      <Route path="/patient/profile" element={<PatientProfilePage />} />
      <Route path="/patient/book-appointment" element={<BookingPage />} />
      <Route path="/patient/appointments" element={<AppointmentsPage />} />
      <Route path="/patient/appointments/:id" element={<AppointmentDetailPage />} />
      <Route path="/patient/medical-records" element={<MedicalRecordsPage />} />
      <Route path="/patient/medical-records/:appointmentId" element={<MedicalRecordDetailPage />} />
      <Route path="/patient/payments" element={<PaymentsPage />} />
      <Route path="/patient/payments/:id" element={<PaymentDetailPage />} />
    </Route>
    <Route element={<ProtectedRoute roles={["DOCTOR"]}><DashboardLayout /></ProtectedRoute>}>
      <Route path="/doctor/dashboard" element={<DoctorDashboardPage />} />
      <Route path="/doctor/appointments" element={<DoctorAppointmentsPage />} />
      <Route path="/doctor/appointments/:id" element={<DoctorAppointmentDetailPage />} />
      <Route path="/doctor/schedules" element={<DoctorSchedulesPage />} />
    </Route>
    <Route element={<ProtectedRoute roles={["STAFF"]}><DashboardLayout /></ProtectedRoute>}>
      <Route path="/staff/dashboard" element={<StaffDashboardPage />} />
      <Route path="/staff/appointments" element={<StaffAppointmentsPage />} />
      <Route path="/staff/payments" element={<StaffPaymentsPage />} />
      <Route path="/staff/payments/:id" element={<StaffPaymentDetailPage />} />
    </Route>
    <Route element={<ProtectedRoute roles={["ADMIN"]}><DashboardLayout /></ProtectedRoute>}>
      <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
      <Route path="/admin/users" element={<AdminUsersPage />} />
      <Route path="/admin/staff" element={<AdminStaffPage />} />
      <Route path="/admin/doctors" element={<AdminDoctorsPage />} />
      <Route path="/admin/doctors/:id" element={<AdminDoctorDetailPage />} />
      <Route path="/admin/services" element={<AdminServicesPage />} />
      <Route path="/admin/appointments" element={<AdminAppointmentsPage />} />
      <Route path="/admin/payments" element={<AdminPaymentsPage />} />
    </Route>
    <Route path="/forbidden" element={<ForbiddenPage />} />
    <Route path="*" element={<NotFoundPage />} />
  </Routes></Suspense>;
}
