package com.dentalclinic.dashboard.service;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentDailySummary;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.dashboard.dto.DashboardSummaryResponse;
import com.dentalclinic.doctor.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Service
public class AdminDashboardService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClinicTimeProvider clinicTimeProvider;

    public AdminDashboardService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            ClinicTimeProvider clinicTimeProvider
    ) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.clinicTimeProvider = clinicTimeProvider;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        LocalDate today = clinicTimeProvider.today();
        Instant rangeStart = clinicTimeProvider.toInstant(today, LocalTime.MIDNIGHT);
        Instant rangeEnd = clinicTimeProvider.toInstant(
                today.plusDays(1),
                LocalTime.MIDNIGHT
        );
        AppointmentDailySummary dailySummary = appointmentRepository
                .summarizeScheduledRange(
                        rangeStart,
                        rangeEnd,
                        AppointmentStatus.CHECKED_IN,
                        AppointmentStatus.IN_PROGRESS,
                        AppointmentStatus.COMPLETED,
                        AppointmentStatus.CANCELLED,
                        AppointmentStatus.NO_SHOW
                );
        return new DashboardSummaryResponse(
                dailySummary.getPatientsVisitedToday(),
                doctorRepository.count(),
                dailySummary.getTodayAppointments(),
                dailySummary.getCompletedToday(),
                dailySummary.getCancelledToday(),
                dailySummary.getNoShowToday()
        );
    }
}
