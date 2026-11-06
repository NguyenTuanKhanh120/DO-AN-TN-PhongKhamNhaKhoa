package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.StartExaminationResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentStartExaminationService;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentStartExaminationServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:15:00Z");

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    private AppointmentStartExaminationService startExaminationService;

    @BeforeEach
    void setUp() {
        startExaminationService = startExaminationServiceAt(FIXED_NOW);
    }

    private AppointmentStartExaminationService startExaminationServiceAt(Instant now) {
        ClinicTimeProvider clinicTimeProvider = new ClinicTimeProvider(
                Clock.fixed(now, CLINIC_ZONE)
        );
        return new AppointmentStartExaminationService(
                doctorRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                new SchedulingPolicy(clinicTimeProvider),
                clinicTimeProvider
        );
    }

    @Test
    void shouldStartOwnedBookedAppointmentAndWriteAudit() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.BOOKED);
        when(doctorRepository.findForUpdateByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));
        when(appointmentRepository.existsOtherByDoctorIdAndStatus(
                doctor.getId(),
                AppointmentStatus.IN_PROGRESS,
                appointment.getId()
        )).thenReturn(false);

        StartExaminationResponse response = startExaminationService.start(
                authenticatedUserId,
                appointment.getId()
        );

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_PROGRESS);
        assertThat(appointment.getActualStartAt()).isEqualTo(FIXED_NOW);
        assertThat(response.id()).isEqualTo(appointment.getId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.IN_PROGRESS);
        assertThat(response.estimatedStartAt().toString())
                .isEqualTo("2026-10-12T08:00+07:00");
        assertThat(response.checkedInAt()).isNull();
        assertThat(response.actualStartAt().toString())
                .isEqualTo("2026-10-12T09:15+07:00");

        verify(appointmentRepository).saveAndFlush(appointment);
        ArgumentCaptor<AppointmentHistory> historyCaptor = ArgumentCaptor.forClass(
                AppointmentHistory.class
        );
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        AppointmentHistory history = historyCaptor.getValue();
        assertThat(history.getAppointment()).isSameAs(appointment);
        assertThat(history.getAction()).isEqualTo(AppointmentAction.START_EXAM);
        assertThat(history.getChangedBy()).isSameAs(doctor.getUser());
        assertThat(history.getReason()).isNull();
        assertThat(history.getDetails().path("old_status").asText())
                .isEqualTo("BOOKED");
        assertThat(history.getDetails().path("new_status").asText())
                .isEqualTo("IN_PROGRESS");
        assertThat(history.getDetails().path("estimated_start_at").asText())
                .isEqualTo("2026-10-12T08:00+07:00");
        assertThat(history.getDetails().path("appointment_date").asText())
                .isEqualTo("2026-10-12");
        assertThat(history.getDetails().path("session").asText())
                .isEqualTo("MORNING");
        assertThat(history.getDetails().path("queue_number").asInt()).isEqualTo(1);
        assertThat(history.getDetails().path("checked_in_at").isNull()).isTrue();
        assertThat(history.getDetails().path("actual_start_at").asText())
                .isEqualTo("2026-10-12T09:15+07:00");
    }

    @Test
    void shouldRejectStartWhenAppointmentIsNotEligible() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.COMPLETED);
        when(doctorRepository.findForUpdateByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> startExaminationService.start(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage(
                        "Chỉ có thể bắt đầu khám cho lịch hẹn đang ở trạng thái BOOKED"
                );

        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    @Test
    void shouldRejectStartWhenDoctorHasAnotherExaminationInProgress() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.BOOKED);
        when(doctorRepository.findForUpdateByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));
        when(appointmentRepository.existsOtherByDoctorIdAndStatus(
                doctor.getId(),
                AppointmentStatus.IN_PROGRESS,
                appointment.getId()
        )).thenReturn(true);

        assertThatThrownBy(() -> startExaminationService.start(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Bác sĩ đang có một ca khám khác chưa hoàn thành");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(appointment.getActualStartAt()).isNull();
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    @Test
    void shouldRejectQueueOneBeforeSessionStarts() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.BOOKED);
        AppointmentStartExaminationService beforeSessionService = startExaminationServiceAt(
                Instant.parse("2026-10-12T00:59:00Z")
        );
        when(doctorRepository.findForUpdateByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> beforeSessionService.start(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Chỉ có thể bắt đầu khám sau khi buổi khám bắt đầu lúc 08:00");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(appointment.getActualStartAt()).isNull();
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void shouldRejectQueueTwoWhileQueueOneIsActive() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = queueTwoAppointment(AppointmentStatus.BOOKED);
        when(doctorRepository.findForUpdateByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));
        when(appointmentRepository.existsActiveDoctorQueue(
                doctor.getId(),
                appointment.getAppointmentDate(),
                ScheduleSession.MORNING,
                (short) 1,
                activeQueueStatuses()
        )).thenReturn(true);

        assertThatThrownBy(() -> startExaminationService.start(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Ca 2 chỉ có thể bắt đầu khi Ca 1 đã hoàn thành, bị hủy, "
                        + "không đến hoặc không tồn tại");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(appointment.getQueueNumber()).isEqualTo((short) 2);
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void shouldStartQueueTwoWithoutRenumberingWhenQueueOneIsNotActive() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = queueTwoAppointment(AppointmentStatus.BOOKED);
        when(doctorRepository.findForUpdateByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));
        when(appointmentRepository.existsActiveDoctorQueue(
                doctor.getId(),
                appointment.getAppointmentDate(),
                ScheduleSession.MORNING,
                (short) 1,
                activeQueueStatuses()
        )).thenReturn(false);
        when(appointmentRepository.existsOtherByDoctorIdAndStatus(
                doctor.getId(),
                AppointmentStatus.IN_PROGRESS,
                appointment.getId()
        )).thenReturn(false);

        StartExaminationResponse response = startExaminationService.start(
                authenticatedUserId,
                appointment.getId()
        );

        assertThat(response.status()).isEqualTo(AppointmentStatus.IN_PROGRESS);
        assertThat(appointment.getQueueNumber()).isEqualTo((short) 2);
        verify(appointmentRepository).saveAndFlush(appointment);
    }

    @Test
    void shouldNotRevealAnotherDoctorsAppointment() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        when(doctorRepository.findForUpdateByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointmentId,
                doctor.getId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> startExaminationService.start(
                authenticatedUserId,
                appointmentId
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldAllowCheckedInAppointmentToContinue() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(AppointmentStatus.CHECKED_IN);
        when(doctorRepository.findForUpdateByUserId(authenticatedUserId))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));
        when(appointmentRepository.existsOtherByDoctorIdAndStatus(
                doctor.getId(),
                AppointmentStatus.IN_PROGRESS,
                appointment.getId()
        )).thenReturn(false);

        StartExaminationResponse response = startExaminationService.start(
                authenticatedUserId,
                appointment.getId()
        );

        assertThat(response.status()).isEqualTo(AppointmentStatus.IN_PROGRESS);
        assertThat(response.checkedInAt()).isNotNull();
    }

    @Test
    void shouldReportMissingDoctorProfile() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        when(doctorRepository.findForUpdateByUserId(authenticatedUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> startExaminationService.start(
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
        appointment.setScheduledStartAt(Instant.parse("2026-10-12T01:00:00Z"));
        appointment.setAppointmentDate(LocalDate.of(2026, 10, 12));
        appointment.setSession(ScheduleSession.MORNING);
        appointment.setQueueNumber((short) 1);
        if (status == AppointmentStatus.CHECKED_IN) {
            appointment.setCheckedInAt(Instant.parse("2026-10-12T01:55:00Z"));
        }
        appointment.setStatus(status);
        return appointment;
    }

    private Appointment queueTwoAppointment(AppointmentStatus status) {
        Appointment appointment = appointment(status);
        appointment.setScheduledStartAt(Instant.parse("2026-10-12T02:30:00Z"));
        appointment.setQueueNumber((short) 2);
        return appointment;
    }

    private List<AppointmentStatus> activeQueueStatuses() {
        return List.of(
                AppointmentStatus.BOOKED,
                AppointmentStatus.CHECKED_IN,
                AppointmentStatus.IN_PROGRESS
        );
    }
}
