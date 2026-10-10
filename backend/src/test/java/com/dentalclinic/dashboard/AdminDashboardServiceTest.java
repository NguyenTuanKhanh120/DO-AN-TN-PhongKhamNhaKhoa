package com.dentalclinic.dashboard;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentDailySummary;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.dashboard.dto.DashboardSummaryResponse;
import com.dentalclinic.dashboard.service.AdminDashboardService;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-10T17:30:00Z");

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentDailySummary dailySummary;

    private AdminDashboardService adminDashboardService;

    @BeforeEach
    void setUp() {
        adminDashboardService = new AdminDashboardService(
                patientRepository,
                doctorRepository,
                appointmentRepository,
                new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE))
        );
    }

    @Test
    void shouldReturnSummaryForCurrentClinicDate() {
        Instant expectedStart = Instant.parse("2026-10-10T17:00:00Z");
        Instant expectedEnd = Instant.parse("2026-10-11T17:00:00Z");
        when(patientRepository.count()).thenReturn(120L);
        when(doctorRepository.count()).thenReturn(8L);
        when(appointmentRepository.summarizeScheduledRange(
                expectedStart,
                expectedEnd,
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.NO_SHOW
        )).thenReturn(dailySummary);
        when(dailySummary.getTodayAppointments()).thenReturn(25L);
        when(dailySummary.getCompletedToday()).thenReturn(14L);
        when(dailySummary.getCancelledToday()).thenReturn(3L);
        when(dailySummary.getNoShowToday()).thenReturn(2L);

        DashboardSummaryResponse result = adminDashboardService.getSummary();

        assertThat(result).isEqualTo(new DashboardSummaryResponse(
                120,
                8,
                25,
                14,
                3,
                2
        ));
        verify(appointmentRepository).summarizeScheduledRange(
                expectedStart,
                expectedEnd,
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.NO_SHOW
        );
    }
}
