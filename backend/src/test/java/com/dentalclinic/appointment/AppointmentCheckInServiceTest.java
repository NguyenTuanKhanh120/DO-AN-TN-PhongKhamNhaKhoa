package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.CheckInAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentCheckInService;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentCheckInServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T01:55:00Z");

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    private AppointmentCheckInService checkInService;

    @BeforeEach
    void setUp() {
        checkInService = new AppointmentCheckInService(
                doctorRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE))
        );
    }

    @Test
    void shouldCheckInOwnedBookedAppointmentAndWriteAudit() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.BOOKED);
        when(doctorRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));

        CheckInAppointmentResponse response = checkInService.checkIn(
                authenticatedUserId,
                appointment.getId()
        );

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CHECKED_IN);
        assertThat(appointment.getCheckedInAt()).isEqualTo(FIXED_NOW);
        assertThat(response.id()).isEqualTo(appointment.getId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.CHECKED_IN);
        assertThat(response.scheduledStartAt().toString())
                .isEqualTo("2026-10-12T09:00+07:00");
        assertThat(response.checkedInAt().toString())
                .isEqualTo("2026-10-12T08:55+07:00");

        verify(appointmentRepository).saveAndFlush(appointment);
        ArgumentCaptor<AppointmentHistory> historyCaptor = ArgumentCaptor.forClass(
                AppointmentHistory.class
        );
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        AppointmentHistory history = historyCaptor.getValue();
        assertThat(history.getAppointment()).isSameAs(appointment);
        assertThat(history.getAction()).isEqualTo(AppointmentAction.CHECK_IN);
        assertThat(history.getChangedBy()).isSameAs(doctor.getUser());
        assertThat(history.getReason()).isNull();
        assertThat(history.getDetails().path("old_status").asText()).isEqualTo("BOOKED");
        assertThat(history.getDetails().path("new_status").asText()).isEqualTo("CHECKED_IN");
        assertThat(history.getDetails().path("scheduled_start_at").asText())
                .isEqualTo("2026-10-12T09:00+07:00");
        assertThat(history.getDetails().path("checked_in_at").asText())
                .isEqualTo("2026-10-12T08:55+07:00");
    }

    @Test
    void shouldRejectCheckInWhenAppointmentIsNotBooked() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.CHECKED_IN);
        appointment.setCheckedInAt(FIXED_NOW.minusSeconds(60));
        when(doctorRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> checkInService.checkIn(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Chỉ có thể check-in lịch hẹn đang ở trạng thái BOOKED");

        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    @Test
    void shouldNotRevealAnotherDoctorsAppointment() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        when(doctorRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointmentId,
                doctor.getId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkInService.checkIn(
                authenticatedUserId,
                appointmentId
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldReportMissingDoctorProfile() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        when(doctorRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkInService.checkIn(
                authenticatedUserId,
                appointmentId
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy hồ sơ bác sĩ");

        verify(appointmentRepository, never()).findForUpdateByIdAndDoctorId(any(), any());
    }

    private Doctor doctor(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setFullName("Bác sĩ kiểm thử");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(user);
        return doctor;
    }

    private Appointment appointment(AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setScheduledStartAt(Instant.parse("2026-10-12T02:00:00Z"));
        appointment.setStatus(status);
        return appointment;
    }
}
