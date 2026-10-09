package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.CancelAppointmentRequest;
import com.dentalclinic.appointment.dto.CancelAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentCancellationService;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentCancellationServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T03:00:00Z");

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    private AppointmentCancellationService cancellationService;

    @BeforeEach
    void setUp() {
        cancellationService = new AppointmentCancellationService(
                patientRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE)),
                90
        );
    }

    @Test
    void shouldAllowCancellationAtExactlyNinetyMinutesAndWriteAudit() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                FIXED_NOW.plusSeconds(90 * 60L),
                AppointmentStatus.BOOKED
        );
        when(patientRepository.findByUserId(authenticatedUserId))
                .thenReturn(Optional.of(patient));
        when(appointmentRepository.findForUpdateByIdAndPatientId(
                appointment.getId(),
                patient.getId()
        )).thenReturn(Optional.of(appointment));

        CancelAppointmentResponse response = cancellationService.cancel(
                authenticatedUserId,
                appointment.getId(),
                new CancelAppointmentRequest("  Bận   việc gia đình  ")
        );

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(response.reason()).isEqualTo("Bận việc gia đình");
        assertThat(response.scheduledStartAt().toString())
                .isEqualTo("2026-10-12T11:30+07:00");
        assertThat(response.cancelledAt().toString())
                .isEqualTo("2026-10-12T10:00+07:00");

        verify(appointmentRepository).saveAndFlush(appointment);
        ArgumentCaptor<AppointmentHistory> historyCaptor = ArgumentCaptor.forClass(
                AppointmentHistory.class
        );
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        AppointmentHistory history = historyCaptor.getValue();
        assertThat(history.getAction()).isEqualTo(AppointmentAction.CANCEL);
        assertThat(history.getChangedBy()).isSameAs(patient.getUser());
        assertThat(history.getReason()).isEqualTo("Bận việc gia đình");
        assertThat(history.getDetails().path("old_status").asText()).isEqualTo("BOOKED");
        assertThat(history.getDetails().path("new_status").asText()).isEqualTo("CANCELLED");
        assertThat(history.getDetails().path("cancelled_at").asText())
                .isEqualTo("2026-10-12T10:00+07:00");
    }

    @Test
    void shouldRejectCancellationBelowNinetyMinutes() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                FIXED_NOW.plusSeconds(90 * 60L - 1),
                AppointmentStatus.BOOKED
        );
        when(patientRepository.findByUserId(authenticatedUserId))
                .thenReturn(Optional.of(patient));
        when(appointmentRepository.findForUpdateByIdAndPatientId(
                appointment.getId(),
                patient.getId()
        )).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> cancellationService.cancel(
                authenticatedUserId,
                appointment.getId(),
                new CancelAppointmentRequest("Bận việc gia đình")
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("dưới 1 giờ 30 phút");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        verify(appointmentRepository, never()).saveAndFlush(appointment);
        verify(appointmentHistoryRepository, never()).saveAndFlush(
                org.mockito.ArgumentMatchers.any(AppointmentHistory.class)
        );
    }

    @Test
    void shouldRejectCancellationWhenAppointmentIsNotBooked() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                FIXED_NOW.plusSeconds(4 * 60 * 60L),
                AppointmentStatus.CHECKED_IN
        );
        when(patientRepository.findByUserId(authenticatedUserId))
                .thenReturn(Optional.of(patient));
        when(appointmentRepository.findForUpdateByIdAndPatientId(
                appointment.getId(),
                patient.getId()
        )).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> cancellationService.cancel(
                authenticatedUserId,
                appointment.getId(),
                new CancelAppointmentRequest("Không thể đến")
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("trạng thái BOOKED");

        verify(appointmentRepository, never()).saveAndFlush(appointment);
    }

    @Test
    void shouldNotRevealAnotherPatientsAppointment() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        when(patientRepository.findByUserId(authenticatedUserId))
                .thenReturn(Optional.of(patient));
        when(appointmentRepository.findForUpdateByIdAndPatientId(
                appointmentId,
                patient.getId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancellationService.cancel(
                authenticatedUserId,
                appointmentId,
                new CancelAppointmentRequest("Không thể đến")
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldRejectNegativeCutoffConfiguration() {
        assertThatThrownBy(() -> new AppointmentCancellationService(
                patientRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE)),
                -1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cấu hình thời hạn hủy lịch không hợp lệ");
    }

    private Patient patient(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setFullName("Bệnh nhân kiểm thử");

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user);
        return patient;
    }

    private Appointment appointment(
            Patient patient,
            Instant scheduledStartAt,
            AppointmentStatus status
    ) {
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setScheduledStartAt(scheduledStartAt);
        appointment.setStatus(status);
        return appointment;
    }
}
