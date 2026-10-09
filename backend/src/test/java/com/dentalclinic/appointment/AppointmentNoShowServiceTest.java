package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.NoShowAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentNoShowService;
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
class AppointmentNoShowServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:15:00Z");

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    private AppointmentNoShowService noShowService;

    @BeforeEach
    void setUp() {
        noShowService = new AppointmentNoShowService(
                doctorRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE)),
                15
        );
    }

    @Test
    void shouldAllowNoShowAtExactlyFifteenMinutesAndWriteAudit() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(
                FIXED_NOW.minusSeconds(15 * 60L),
                AppointmentStatus.BOOKED
        );
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);

        NoShowAppointmentResponse response = noShowService.markNoShow(
                authenticatedUserId,
                appointment.getId()
        );

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(response.id()).isEqualTo(appointment.getId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(response.scheduledStartAt().toString())
                .isEqualTo("2026-10-12T09:00+07:00");
        assertThat(response.noShowAt().toString())
                .isEqualTo("2026-10-12T09:15+07:00");

        verify(appointmentRepository).saveAndFlush(appointment);
        ArgumentCaptor<AppointmentHistory> historyCaptor = ArgumentCaptor.forClass(
                AppointmentHistory.class
        );
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        AppointmentHistory history = historyCaptor.getValue();
        assertThat(history.getAppointment()).isSameAs(appointment);
        assertThat(history.getAction()).isEqualTo(AppointmentAction.NO_SHOW);
        assertThat(history.getChangedBy()).isSameAs(doctor.getUser());
        assertThat(history.getReason()).isNull();
        assertThat(history.getDetails().path("old_status").asText()).isEqualTo("BOOKED");
        assertThat(history.getDetails().path("new_status").asText()).isEqualTo("NO_SHOW");
        assertThat(history.getDetails().path("scheduled_start_at").asText())
                .isEqualTo("2026-10-12T09:00+07:00");
        assertThat(history.getDetails().path("no_show_at").asText())
                .isEqualTo("2026-10-12T09:15+07:00");
    }

    @Test
    void shouldRejectNoShowOneSecondBeforeGraceExpires() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(
                FIXED_NOW.minusSeconds(15 * 60L - 1),
                AppointmentStatus.BOOKED
        );
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);

        assertThatThrownBy(() -> noShowService.markNoShow(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Chỉ có thể xác nhận NO_SHOW sau giờ hẹn ít nhất 15 phút");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    @Test
    void shouldRejectNoShowAfterPatientCheckedIn() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(
                FIXED_NOW.minusSeconds(30 * 60L),
                AppointmentStatus.CHECKED_IN
        );
        appointment.setCheckedInAt(FIXED_NOW.minusSeconds(20 * 60L));
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);

        assertThatThrownBy(() -> noShowService.markNoShow(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Không thể đánh dấu NO_SHOW vì bệnh nhân đã check-in");

        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void shouldRejectBookedRecordThatAlreadyHasCheckInTimestamp() {
        UUID authenticatedUserId = UUID.randomUUID();
        Doctor doctor = doctor(authenticatedUserId);
        Appointment appointment = appointment(
                FIXED_NOW.minusSeconds(30 * 60L),
                AppointmentStatus.BOOKED
        );
        appointment.setCheckedInAt(FIXED_NOW.minusSeconds(20 * 60L));
        stubOwnedAppointment(authenticatedUserId, doctor, appointment);

        assertThatThrownBy(() -> noShowService.markNoShow(
                authenticatedUserId,
                appointment.getId()
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Không thể đánh dấu NO_SHOW vì bệnh nhân đã check-in");
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

        assertThatThrownBy(() -> noShowService.markNoShow(
                authenticatedUserId,
                appointmentId
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldRejectNegativeGraceConfiguration() {
        assertThatThrownBy(() -> new AppointmentNoShowService(
                doctorRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE)),
                -1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cấu hình thời gian chờ NO_SHOW không hợp lệ");
    }

    private void stubOwnedAppointment(
            UUID authenticatedUserId,
            Doctor doctor,
            Appointment appointment
    ) {
        when(doctorRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findForUpdateByIdAndDoctorId(
                appointment.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(appointment));
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

    private Appointment appointment(Instant scheduledStartAt, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setScheduledStartAt(scheduledStartAt);
        appointment.setStatus(status);
        return appointment;
    }
}
