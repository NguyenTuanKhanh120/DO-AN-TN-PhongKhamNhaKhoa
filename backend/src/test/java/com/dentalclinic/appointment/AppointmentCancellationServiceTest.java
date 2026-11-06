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
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.SchedulingPolicy;
import com.dentalclinic.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-11T21:59:00Z");

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    private AppointmentCancellationService cancellationService;

    @BeforeEach
    void setUp() {
        cancellationService = cancellationServiceAt(FIXED_NOW);
    }

    private AppointmentCancellationService cancellationServiceAt(Instant now) {
        ClinicTimeProvider timeProvider = new ClinicTimeProvider(Clock.fixed(now, CLINIC_ZONE));
        cancellationService = new AppointmentCancellationService(
                patientRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                timeProvider,
                new SchedulingPolicy(timeProvider)
        );
        return cancellationService;
    }

    @Test
    void shouldAllowCancellationAt0459AndWriteAudit() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                toInstant(MONDAY, LocalTime.of(14, 0)),
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
        assertThat(response.appointmentDate()).isEqualTo(MONDAY);
        assertThat(response.session()).isEqualTo(ScheduleSession.AFTERNOON);
        assertThat(response.queueNumber()).isEqualTo(1);
        assertThat(response.reason()).isEqualTo("Bận việc gia đình");
        assertThat(response.estimatedStartAt().toString())
                .isEqualTo("2026-10-12T14:00+07:00");
        assertThat(response.cancelledAt().toString())
                .isEqualTo("2026-10-12T04:59+07:00");

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
        assertThat(history.getDetails().path("appointment_date").asText())
                .isEqualTo("2026-10-12");
        assertThat(history.getDetails().path("session").asText()).isEqualTo("AFTERNOON");
        assertThat(history.getDetails().path("queue_number").asInt()).isEqualTo(1);
        assertThat(history.getDetails().path("estimated_start_at").asText())
                .isEqualTo("2026-10-12T14:00+07:00");
        assertThat(history.getDetails().path("cancelled_at").asText())
                .isEqualTo("2026-10-12T04:59+07:00");
    }

    @Test
    void shouldRejectCancellationAt0500() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                toInstant(MONDAY, LocalTime.of(14, 0)),
                AppointmentStatus.BOOKED
        );
        AppointmentCancellationService atCutoffService = cancellationServiceAt(
                Instant.parse("2026-10-11T22:00:00Z")
        );
        when(patientRepository.findByUserId(authenticatedUserId))
                .thenReturn(Optional.of(patient));
        when(appointmentRepository.findForUpdateByIdAndPatientId(
                appointment.getId(),
                patient.getId()
        )).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> atCutoffService.cancel(
                authenticatedUserId,
                appointment.getId(),
                new CancelAppointmentRequest("Bận việc gia đình")
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Không thể tự hủy lịch từ 05:00 ngày khám. "
                        + "Vui lòng liên hệ phòng khám nếu có trường hợp đặc biệt.");

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
                toInstant(MONDAY, LocalTime.of(14, 0)),
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
    void shouldAllowCancellingFutureDateAfterCurrentDayCutoff() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        LocalDate nextMonday = MONDAY.plusWeeks(1);
        Appointment appointment = appointment(
                patient,
                toInstant(nextMonday, LocalTime.of(14, 0)),
                AppointmentStatus.BOOKED
        );
        AppointmentCancellationService afterCurrentCutoff = cancellationServiceAt(
                Instant.parse("2026-10-11T22:00:00Z")
        );
        when(patientRepository.findByUserId(authenticatedUserId))
                .thenReturn(Optional.of(patient));
        when(appointmentRepository.findForUpdateByIdAndPatientId(
                appointment.getId(), patient.getId()
        )).thenReturn(Optional.of(appointment));

        CancelAppointmentResponse response = afterCurrentCutoff.cancel(
                authenticatedUserId,
                appointment.getId(),
                new CancelAppointmentRequest("Bận việc")
        );

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
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
        var scheduledCase = scheduledStartAt.atZone(CLINIC_ZONE);
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setAppointmentDate(scheduledCase.toLocalDate());
        appointment.setSession(ScheduleSession.AFTERNOON);
        appointment.setQueueNumber((short) 1);
        appointment.setScheduledStartAt(scheduledStartAt);
        appointment.setStatus(status);
        return appointment;
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(CLINIC_ZONE).toInstant();
    }
}
