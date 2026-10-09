package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.RescheduleAppointmentRequest;
import com.dentalclinic.appointment.dto.RescheduleAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentRescheduleService;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.service.SlotService;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentRescheduleServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:30:00Z");
    private static final String SLOT_CONFLICT_MESSAGE = "Khung giờ mới vừa được người khác đặt. "
            + "Lịch hiện tại của bạn vẫn được giữ nguyên. Vui lòng chọn khung giờ khác.";

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    @Mock
    private SlotService slotService;

    private AppointmentRescheduleService rescheduleService;

    @BeforeEach
    void setUp() {
        rescheduleService = new AppointmentRescheduleService(
                patientRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                slotService,
                new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE)),
                90,
                60
        );
    }

    @Test
    void shouldAllowAtExactCutoffAndWriteAudit() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                FIXED_NOW.plusSeconds(90 * 60L),
                AppointmentStatus.BOOKED
        );
        Instant newStart = FIXED_NOW.plusSeconds(30 * 60L);
        stubOwnedAppointment(authenticatedUserId, patient, appointment);
        when(slotService.requireAvailableRescheduleSlot(
                appointment.getDoctorService().getDoctor().getId(),
                appointment.getDoctorService().getService().getId(),
                newStart
        )).thenReturn(appointment.getDoctorService());

        RescheduleAppointmentResponse response = rescheduleService.reschedule(
                authenticatedUserId,
                appointment.getId(),
                new RescheduleAppointmentRequest(
                        OffsetDateTime.parse("2026-10-12T10:00:00+07:00"),
                        "  Bận   việc gia đình  "
                )
        );

        assertThat(appointment.getScheduledStartAt()).isEqualTo(newStart);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.id()).isEqualTo(appointment.getId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.oldScheduledStartAt().toString())
                .isEqualTo("2026-10-12T11:00+07:00");
        assertThat(response.scheduledStartAt().toString())
                .isEqualTo("2026-10-12T10:00+07:00");
        assertThat(response.scheduledEndAt().toString())
                .isEqualTo("2026-10-12T11:00+07:00");
        assertThat(response.reason()).isEqualTo("Bận việc gia đình");

        verify(appointmentRepository).saveAndFlush(appointment);
        ArgumentCaptor<AppointmentHistory> historyCaptor = ArgumentCaptor.forClass(
                AppointmentHistory.class
        );
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        AppointmentHistory history = historyCaptor.getValue();
        assertThat(history.getAppointment()).isSameAs(appointment);
        assertThat(history.getAction()).isEqualTo(AppointmentAction.RESCHEDULE);
        assertThat(history.getChangedBy()).isSameAs(patient.getUser());
        assertThat(history.getReason()).isEqualTo("Bận việc gia đình");
        assertThat(history.getDetails().path("old_date").asText()).isEqualTo("2026-10-12");
        assertThat(history.getDetails().path("old_start_time").asText()).isEqualTo("11:00");
        assertThat(history.getDetails().path("new_date").asText()).isEqualTo("2026-10-12");
        assertThat(history.getDetails().path("new_start_time").asText()).isEqualTo("10:00");
        assertThat(history.getDetails().path("changed_at").asText())
                .isEqualTo("2026-10-12T09:30+07:00");
    }

    @Test
    void shouldRejectRescheduleBelowNinetyMinutes() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                FIXED_NOW.plusSeconds(90 * 60L - 1),
                AppointmentStatus.BOOKED
        );
        stubOwnedAppointment(authenticatedUserId, patient, appointment);

        assertThatThrownBy(() -> rescheduleService.reschedule(
                authenticatedUserId,
                appointment.getId(),
                request(FIXED_NOW.plusSeconds(3 * 60 * 60L))
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("dưới 1 giờ 30 phút");

        verify(slotService, never()).requireAvailableRescheduleSlot(any(), any(), any());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    @Test
    void shouldRejectRescheduleWhenAppointmentIsNotBooked() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                FIXED_NOW.plusSeconds(4 * 60 * 60L),
                AppointmentStatus.COMPLETED
        );
        stubOwnedAppointment(authenticatedUserId, patient, appointment);

        assertThatThrownBy(() -> rescheduleService.reschedule(
                authenticatedUserId,
                appointment.getId(),
                request(FIXED_NOW.plusSeconds(5 * 60 * 60L))
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("trạng thái BOOKED");

        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void shouldRejectCurrentSlotAsNewSlot() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                FIXED_NOW.plusSeconds(4 * 60 * 60L),
                AppointmentStatus.BOOKED
        );
        stubOwnedAppointment(authenticatedUserId, patient, appointment);

        assertThatThrownBy(() -> rescheduleService.reschedule(
                authenticatedUserId,
                appointment.getId(),
                request(appointment.getScheduledStartAt())
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
            assertThat(exception).hasMessage("Khung giờ mới phải khác khung giờ hiện tại");
        });

        verify(slotService, never()).requireAvailableRescheduleSlot(any(), any(), any());
    }

    @Test
    void shouldNotRevealAnotherPatientsAppointment() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        when(patientRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findForUpdateByIdAndPatientId(
                appointmentId,
                patient.getId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rescheduleService.reschedule(
                authenticatedUserId,
                appointmentId,
                request(FIXED_NOW.plusSeconds(4 * 60 * 60L))
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldTranslateUniqueRaceToConflictWithoutWritingHistory() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                FIXED_NOW.plusSeconds(4 * 60 * 60L),
                AppointmentStatus.BOOKED
        );
        Instant newStart = FIXED_NOW.plusSeconds(30 * 60L);
        stubOwnedAppointment(authenticatedUserId, patient, appointment);
        when(slotService.requireAvailableRescheduleSlot(
                appointment.getDoctorService().getDoctor().getId(),
                appointment.getDoctorService().getService().getId(),
                newStart
        )).thenReturn(appointment.getDoctorService());
        doThrow(new DataIntegrityViolationException(
                "duplicate slot",
                new SQLException("duplicate slot", "23505")
        )).when(appointmentRepository).saveAndFlush(appointment);

        assertThatThrownBy(() -> rescheduleService.reschedule(
                authenticatedUserId,
                appointment.getId(),
                request(newStart)
        )).isInstanceOf(ConflictException.class)
                .hasMessage(SLOT_CONFLICT_MESSAGE);

        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    private void stubOwnedAppointment(
            UUID authenticatedUserId,
            Patient patient,
            Appointment appointment
    ) {
        when(patientRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findForUpdateByIdAndPatientId(
                appointment.getId(),
                patient.getId()
        )).thenReturn(Optional.of(appointment));
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
        User doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setFullName("Bác sĩ kiểm thử");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(doctorUser);

        DentalService dentalService = new DentalService();
        dentalService.setId(UUID.randomUUID());
        dentalService.setName("Trám răng");

        DoctorService doctorService = new DoctorService();
        doctorService.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        doctorService.setDoctor(doctor);
        doctorService.setService(dentalService);

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setScheduledStartAt(scheduledStartAt);
        appointment.setStatus(status);
        return appointment;
    }

    private RescheduleAppointmentRequest request(Instant startAt) {
        return new RescheduleAppointmentRequest(
                startAt.atZone(CLINIC_ZONE).toOffsetDateTime(),
                "Bận việc gia đình"
        );
    }
}
